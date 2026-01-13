package com.dsm.mapstruct;

import com.dsm.mapstruct.model.CompletionResult;
import com.dsm.mapstruct.model.FieldInfo;
import com.dsm.mapstruct.model.PathSegment;
import com.dsm.mapstruct.util.CollectionTypeResolver;
import com.dsm.mapstruct.util.NameMatcher;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Navigates through class structures following MapStruct path expressions.
 */
public class PathNavigator {

    private final PathParser pathParser;
    private final ReflectionAnalyzer reflectionAnalyzer;
    private final CollectionTypeResolver collectionTypeResolver;
    private final NameMatcher nameMatcher;

    public PathNavigator() {
        this.pathParser = new PathParser();
        this.reflectionAnalyzer = new ReflectionAnalyzer();
        this.collectionTypeResolver = new CollectionTypeResolver();
        this.nameMatcher = new NameMatcher();
    }

    /**
     * Checks if a type is a terminal type that shouldn't have completions.
     * This includes primitives, wrapper types, String, and common java.lang types.
     */
    private boolean isTerminalType(Class<?> clazz) {
        // Primitive types
        if (clazz.isPrimitive()) {
            return true;
        }

        // Wrapper types and String
        return clazz == String.class ||
               clazz == Integer.class ||
               clazz == Long.class ||
               clazz == Double.class ||
               clazz == Float.class ||
               clazz == Boolean.class ||
               clazz == Byte.class ||
               clazz == Short.class ||
               clazz == Character.class;
    }

    /**
     * Navigates through the path and returns completion candidates.
     *
     * @param rootClass the starting class
     * @param pathExpression the MapStruct path expression
     * @return completion result with available fields/getters
     */
    public CompletionResult navigate(Class<?> rootClass, String pathExpression) {
        try {
            List<PathSegment> segments = pathParser.parse(pathExpression);

            if (segments.isEmpty()) {
                // No path - check if root class is terminal type
                if (isTerminalType(rootClass)) {
                    return CompletionResult.empty(rootClass.getName(), pathExpression);
                }
                // Return all fields and getters from root class
                List<FieldInfo> allFields = reflectionAnalyzer.getAllFieldsAndGetters(rootClass);
                return CompletionResult.of(rootClass.getName(), pathExpression, allFields);
            }

            // Navigate through the path
            Class<?> currentType = rootClass;
            Field lastField = null;

            for (int i = 0; i < segments.size() - 1; i++) {
                PathSegment segment = segments.get(i);
                Class<?> nextType = resolveNextType(currentType, segment, lastField);

                if (nextType == null) {
                    // Cannot navigate further
                    return CompletionResult.empty(rootClass.getName(), pathExpression);
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
                        return CompletionResult.empty(rootClass.getName(), pathExpression);
                    }
                    currentType = nextType;
                }
                prefix = ""; // Show all completions
            }

            // Check if current type is terminal - return empty if so
            if (isTerminalType(currentType)) {
                return CompletionResult.empty(currentType.getName(), pathExpression);
            }

            // Get all fields and getters from current type
            List<FieldInfo> allFields = reflectionAnalyzer.getAllFieldsAndGetters(currentType);

            // Filter by prefix if needed
            List<FieldInfo> filtered = nameMatcher.filterByPrefix(allFields, prefix);

            return CompletionResult.of(currentType.getName(), pathExpression, filtered);

        } catch (Exception e) {
            // Return empty result on error
            return CompletionResult.empty(rootClass.getName(), pathExpression);
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
            if (collectionTypeResolver.isMapStructCollectionProperty(segmentName) &&
                collectionTypeResolver.isCollection(currentType)) {

                // This is a collection property accessor - resolve to item type
                if (currentType.isArray()) {
                    return currentType.getComponentType();
                }

                // For List/Collection types, resolve generic type from last field
                if (lastField != null && collectionTypeResolver.supportsCollectionAccessors(currentType)) {
                    Class<?> itemType = collectionTypeResolver.resolveCollectionItemType(
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
            if (collectionTypeResolver.isCollectionAccessor(methodName)) {
                // Need to resolve the item type from the current type (which should be a collection)
                if (collectionTypeResolver.isCollection(currentType)) {
                    // Arrays: MapStruct doesn't support method calls on arrays
                    // Arrays should use array indexing like items[0] in MapStruct mappings
                    if (currentType.isArray()) {
                        // For tool purposes, we still allow it and return component type
                        // but note that this won't work in actual MapStruct mappings
                        return currentType.getComponentType();
                    }

                    // Check if this collection type actually supports the accessor method
                    if (!collectionTypeResolver.supportsCollectionAccessors(currentType)) {
                        // Return null to indicate invalid navigation
                        return null;
                    }

                    // Try to get generic type from last field
                    if (lastField != null) {
                        Class<?> itemType = collectionTypeResolver.resolveCollectionItemType(
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
