package com.dsm.mapstruct.core.usecase.helper;

import com.dsm.mapstruct.core.model.CompletionResult;
import com.dsm.mapstruct.core.model.FieldInfo;
import com.dsm.mapstruct.core.model.PathSegment;
import com.dsm.mapstruct.core.util.CollectionTypeResolverUtil;
import com.dsm.mapstruct.core.util.NameMatcherUtil;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import one.util.streamex.StreamEx;

import java.lang.reflect.Field;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Navigates through class structures following MapStruct path expressions.
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PathNavigator {

    private static Set<Class<?>> TERMNAL_REF_TYPES = Set.of(
            CharSequence.class,
            Number.class,
            Boolean.class,
            Character.class,
            Date.class,
            Temporal.class
    );

    PathParser pathParser = new PathParser();
    ReflectionAnalyzer reflectionAnalyzer = new ReflectionAnalyzer();

    /**
     * Checks if a type is a terminal type that shouldn't have completions.
     * This includes primitives, wrapper types, String, and common java.lang types.
     */
    private boolean isTerminalType(Class<?> clazz) {
        // Primitive types
        if (clazz.isPrimitive()) {
            return true;
        }

        return StreamEx.of(TERMNAL_REF_TYPES)
                .anyMatch(terminalType -> terminalType.isAssignableFrom(clazz));
    }

    /**
     * Navigates through the path and returns completion candidates.
     *
     * @param rootClass      the starting class
     * @param pathExpression the MapStruct path expression
     * @return completion result with available fields/getters
     */
    public CompletionResult navigate(Class<?> rootClass, String pathExpression) {
        try {
            List<PathSegment> segments = pathParser.parse(pathExpression);

            if (segments.isEmpty()) {
                // No path - check if root class is terminal type
                if (isTerminalType(rootClass)) {
                    return CompletionResult.empty(rootClass.getName(),
                            rootClass.getSimpleName(),
                            rootClass.getPackageName(),
                            pathExpression);
                }
                // Return all fields and getters from root class
                List<FieldInfo> allFields = reflectionAnalyzer.getAllFieldsAndGetters(rootClass);
                return CompletionResult.of(rootClass.getName(),
                        rootClass.getSimpleName(),
                        rootClass.getPackageName(),
                        pathExpression, allFields);
            }

            // Navigate through the path
            Class<?> currentType = rootClass;
            Field lastField = null;

            for (int i = 0; i < segments.size() - 1; i++) {
                PathSegment segment = segments.get(i);
                Class<?> nextType = resolveNextType(currentType, segment, lastField);

                if (nextType == null) {
                    // Cannot navigate further
                    return CompletionResult.empty(rootClass.getName(),
                            rootClass.getSimpleName(),
                            rootClass.getPackageName(),
                            pathExpression);
                }

                // Update lastField if this was a field access
                if (segment.type() == PathSegment.SegmentType.FIELD) {
                    lastField = findField(currentType, segment.name());
                }

                currentType = nextType;
            }

            // Handle the last segment
            PathSegment lastSegment = segments.get(segments.size() - 1);
            String prefix = lastSegment.name();

            // If last segment is empty (trailing dot) or is a complete field/method,
            // navigate to it first
            if (prefix.isEmpty() || lastSegment.type() == PathSegment.SegmentType.METHOD) {
                if (!prefix.isEmpty()) {
                    Class<?> nextType = resolveNextType(currentType, lastSegment, lastField);
                    if (nextType == null) {
                        return CompletionResult.empty(rootClass.getName(),
                                rootClass.getSimpleName(),
                                rootClass.getPackageName(),
                                pathExpression);
                    }
                    currentType = nextType;
                }
                prefix = ""; // Show all completions
            }

            // Check if current type is terminal - return empty if so
            if (isTerminalType(currentType)) {
                return CompletionResult.empty(currentType.getName(),
                        currentType.getSimpleName(),
                        currentType.getPackageName(),
                        pathExpression);
            }

            // Get all fields and getters from current type
            List<FieldInfo> allFields = reflectionAnalyzer.getAllFieldsAndGetters(currentType);

            // Filter by prefix if needed
            List<FieldInfo> filtered = NameMatcherUtil.filterByPrefix(allFields, prefix);

            return CompletionResult.of(currentType.getName(),
                    currentType.getSimpleName(),
                    currentType.getPackageName(),
                    pathExpression,
                    filtered);

        } catch (Exception e) {
            // Return empty result on error
            return CompletionResult.empty(rootClass.getName(),
                    rootClass.getSimpleName(),
                    rootClass.getPackageName(),
                    pathExpression);
        }
    }

    /**
     * Resolves the next type when navigating through a segment.
     */
    private Class<?> resolveNextType(Class<?> currentType, PathSegment segment, Field lastField) {
        if (segment.type() == PathSegment.SegmentType.FIELD) {
            String segmentName = segment.name();

            // Check if this is a MapStruct collection accessor property (first, last, etc.)
            // MapStruct uses property syntax: orders.first (not orders.getFirst())
            if (CollectionTypeResolverUtil.isMapStructCollectionProperty(segmentName) && CollectionTypeResolverUtil.isCollection(currentType)) {

                // This is a collection property accessor - resolve to item type
                if (currentType.isArray()) {
                    return currentType.getComponentType();
                }

                // For List/Collection types, resolve generic type from last field
                if (lastField != null && CollectionTypeResolverUtil.supportsCollectionAccessors(currentType)) {
                    Class<?> itemType = CollectionTypeResolverUtil.resolveCollectionItemType(
                            lastField.getDeclaringClass(),
                            lastField.getName()
                    );
                    if (itemType != null && itemType != Object.class) {
                        return itemType;
                    }
                }

                // Fallback for raw collections
                return Object.class;
            }

            // Regular field access
            Class<?> fieldType = reflectionAnalyzer.getFieldOrGetterType(currentType, segmentName);

            if (fieldType == null) {
                return null;
            }

            // If it's a collection, we might need to get the item type later
            // For now, just return the field type
            return fieldType;

        } else if (segment.type() == PathSegment.SegmentType.METHOD) {
            // Method call
            String methodName = segment.name();

            // Check if it's a collection accessor (getFirst, get, etc.)
            if (CollectionTypeResolverUtil.isCollectionAccessor(methodName)) {
                // Need to resolve the item type from the current type (which should be a collection)
                if (CollectionTypeResolverUtil.isCollection(currentType)) {
                    // Arrays: MapStruct doesn't support method calls on arrays
                    // Arrays should use array indexing like items[0] in MapStruct mappings
                    if (currentType.isArray()) {
                        // For tool purposes, we still allow it and return component type
                        // but note that this won't work in actual MapStruct mappings
                        return currentType.getComponentType();
                    }

                    // Check if this collection type actually supports the accessor method
                    if (!CollectionTypeResolverUtil.supportsCollectionAccessors(currentType)) {
                        // Return null to indicate invalid navigation
                        return null;
                    }

                    // Try to get generic type from last field
                    if (lastField != null) {
                        Class<?> itemType = CollectionTypeResolverUtil.resolveCollectionItemType(
                                lastField.getDeclaringClass(),
                                lastField.getName()
                        );
                        if (itemType != null && itemType != Object.class) {
                            return itemType;
                        }
                    }

                    // Fallback: return Object for raw collections
                    return Object.class;
                }
            }

            // Regular method call
            Class<?> returnType = reflectionAnalyzer.getMethodReturnType(currentType, methodName);
            return returnType;
        }

        return null;
    }

    /**
     * Finds a field in a class hierarchy.
     */
    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Finds a method in a class.
     */
    private java.lang.reflect.Method findMethod(Class<?> clazz, String methodName) {
        for (var method : clazz.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }

        // Check interfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            var method = findMethod(iface, methodName);
            if (method != null) {
                return method;
            }
        }

        return null;
    }
}
