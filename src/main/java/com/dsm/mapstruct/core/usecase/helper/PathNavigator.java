package com.dsm.mapstruct.core.usecase.helper;

import com.dsm.mapstruct.core.model.CompletionResult;
import com.dsm.mapstruct.core.model.FieldInfo;
import com.dsm.mapstruct.core.model.FieldInfo.FieldKind;
import com.dsm.mapstruct.core.model.PathSegment;
import com.dsm.mapstruct.core.model.SourceParameter;
import com.dsm.mapstruct.core.util.CollectionTypeResolverUtil;
import com.dsm.mapstruct.core.util.NameMatcherUtil;
import lombok.AccessLevel;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;
import one.util.streamex.StreamEx;

import java.lang.reflect.Field;
import java.time.temporal.Temporal;
import java.util.ArrayList;
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
     * Navigates from multiple source parameters (multi-param mapper support).
     * Handles parameter name completion and navigation from specific parameters.
     *
     * @param sources        list of source parameters
     * @param pathExpression the MapStruct path expression
     * @param isEnum         true if this is for @ValueMapping (enum constants)
     * @return completion result with parameter names or field completions
     */
    @SneakyThrows
    public CompletionResult navigateFromSources(List<SourceParameter> sources, String pathExpression, boolean isEnum) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("sources list cannot be null or empty");
        }

        // SPECIAL CASE: Single parameter + empty path -> navigate directly into that type
        // This handles:
        // 1. Target attributes (always single-parameter, should show fields not parameter name)
        // 2. Single-parameter source mappers (backward compatibility)
        if ((pathExpression == null || pathExpression.isBlank()) && sources.size() == 1) {
            SourceParameter singleParam = sources.get(0);
            Class<?> paramType = Class.forName(singleParam.type());

            // Detect if this is a target completion (synthetic "$target" parameter name)
            boolean isTargetCompletion = "$target".equals(singleParam.name());

            return navigate(paramType, "", isEnum, isTargetCompletion);
        }

        // Empty path with multiple parameters -> return parameter names as completions
        if (pathExpression == null || pathExpression.isBlank()) {
            return buildParameterCompletions(sources, "");
        }

        // Check if path starts with a parameter name
        String firstSegment = extractFirstSegment(pathExpression);

        // Try to find matching parameter
        SourceParameter matchedParam = findParameterByName(sources, firstSegment);

        if (matchedParam != null) {
            // Path starts with parameter name - navigate from that parameter's type
            String remainingPath = removeFirstSegment(pathExpression);
            Class<?> paramType = Class.forName(matchedParam.type());
            return navigate(paramType, remainingPath, isEnum);
        }

        // Check if it's a partial parameter name (prefix matching)
        List<SourceParameter> matchingParams = filterParametersByPrefix(sources, firstSegment);
        if (!matchingParams.isEmpty() && !firstSegment.contains(".")) {
            // Return matching parameter names
            return buildParameterCompletions(matchingParams, firstSegment);
        }

        // BACKWARD COMPATIBILITY: For single-parameter mappers, if path doesn't match
        // the parameter name, navigate directly from that parameter's type.
        // This handles cases like: CompletePersonDTO map(Person person)
        // where user types "address." but we send sources=[{name:"param0", type:"Person"}]
        if (sources.size() == 1) {
            SourceParameter singleParam = sources.get(0);
            Class<?> paramType = Class.forName(singleParam.type());
            return navigate(paramType, pathExpression, isEnum);
        }

        // Path doesn't match any parameter - return empty
        return CompletionResult.empty("", "", "", pathExpression);
    }

    /**
     * Navigates through the path and returns completion candidates.
     *
     * @param rootClass      the starting class
     * @param pathExpression the MapStruct path expression
     * @return completion result with available fields/getters
     */
    public CompletionResult navigate(Class<?> rootClass, String pathExpression) {
        return navigate(rootClass, pathExpression, false, false);
    }

    /**
     * Navigates through the path and returns completion candidates.
     *
     * @param rootClass      the starting class
     * @param pathExpression the MapStruct path expression
     * @param isEnum         true if this is for @ValueMapping (enum constants)
     * @return completion result with available fields/getters or enum constants
     */
    public CompletionResult navigate(Class<?> rootClass, String pathExpression, boolean isEnum) {
        return navigate(rootClass, pathExpression, isEnum, false);
    }

    /**
     * Navigates through the path and returns completion candidates.
     *
     * @param rootClass          the starting class
     * @param pathExpression     the MapStruct path expression
     * @param isEnum             true if this is for @ValueMapping (enum constants)
     * @param isTargetCompletion true if this is for target attribute completion
     * @return completion result with available fields/getters or enum constants
     */
    public CompletionResult navigate(Class<?> rootClass, String pathExpression, boolean isEnum, boolean isTargetCompletion) {
        try {
            List<PathSegment> segments = pathParser.parse(pathExpression);

            if (segments.isEmpty()) {
                // For enum types (@ValueMapping), return enum constants
                if (isEnum && rootClass.isEnum()) {
                    List<FieldInfo> enumConstants = reflectionAnalyzer.getEnumConstants(rootClass);
                    return CompletionResult.of(rootClass.getName(),
                            rootClass.getSimpleName(),
                            rootClass.getPackageName(),
                            pathExpression, enumConstants);
                }

                // No path - check if root class is terminal type
                if (isTerminalType(rootClass)) {
                    return CompletionResult.empty(rootClass.getName(),
                            rootClass.getSimpleName(),
                            rootClass.getPackageName(),
                            pathExpression);
                }
                // Return all fields and getters from root class
                List<FieldInfo> allFields = reflectionAnalyzer.getAllFieldsAndGetters(rootClass);
                // For target completions, convert field kinds to SETTER
                List<FieldInfo> resultFields = isTargetCompletion ? convertToSetterKind(allFields) : allFields;
                return CompletionResult.of(rootClass.getName(),
                        rootClass.getSimpleName(),
                        rootClass.getPackageName(),
                        pathExpression, resultFields);
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

            // Apply context-specific filtering
            List<FieldInfo> resultFields;
            if (isTargetCompletion) {
                // For target completions, convert field kinds to SETTER
                resultFields = convertToSetterKind(filtered);
            } else {
                // For source completions, filter out SETTER kind (keep only FIELD, GETTER, PARAMETER)
                resultFields = filterOutSetters(filtered);
            }

            return CompletionResult.of(currentType.getName(),
                    currentType.getSimpleName(),
                    currentType.getPackageName(),
                    pathExpression,
                    resultFields);

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

    // ========== Multi-Parameter Support Helper Methods ==========

    /**
     * Builds completion result containing parameter names.
     */
    private CompletionResult buildParameterCompletions(List<SourceParameter> parameters, String pathExpression) {
        List<FieldInfo> parameterFields = new ArrayList<>();

        for (SourceParameter param : parameters) {
            parameterFields.add(new FieldInfo(
                param.name(),
                param.type(),
                FieldKind.PARAMETER
            ));
        }

        // Use first parameter's type info for metadata (or empty if no params)
        if (!parameters.isEmpty()) {
            SourceParameter first = parameters.get(0);
            try {
                Class<?> firstClass = Class.forName(first.type());
                return CompletionResult.of(
                    firstClass.getName(),
                    firstClass.getSimpleName(),
                    firstClass.getPackageName(),
                    pathExpression,
                    parameterFields
                );
            } catch (ClassNotFoundException e) {
                // Fallback to simple class info
            }
        }

        return CompletionResult.of("", "", "", pathExpression, parameterFields);
    }

    /**
     * Extracts the first segment from a path expression.
     * Examples:
     * - "person" -> "person"
     * - "person.address" -> "person"
     * - "person.address.street" -> "person"
     */
    private String extractFirstSegment(String pathExpression) {
        if (pathExpression == null || pathExpression.isBlank()) {
            return "";
        }

        int dotIndex = pathExpression.indexOf('.');
        if (dotIndex == -1) {
            return pathExpression.trim();
        }

        return pathExpression.substring(0, dotIndex).trim();
    }

    /**
     * Removes the first segment from a path expression.
     * Examples:
     * - "person.address" -> "address"
     * - "person.address.street" -> "address.street"
     * - "person" -> ""
     */
    private String removeFirstSegment(String pathExpression) {
        if (pathExpression == null || pathExpression.isBlank()) {
            return "";
        }

        int dotIndex = pathExpression.indexOf('.');
        if (dotIndex == -1) {
            return "";
        }

        return pathExpression.substring(dotIndex + 1);
    }

    /**
     * Finds a parameter by exact name match.
     */
    private SourceParameter findParameterByName(List<SourceParameter> sources, String name) {
        return sources.stream()
            .filter(p -> p.name().equals(name))
            .findFirst()
            .orElse(null);
    }

    /**
     * Filters parameters by prefix (case-insensitive).
     */
    private List<SourceParameter> filterParametersByPrefix(List<SourceParameter> sources, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return sources;
        }

        String lowerPrefix = prefix.toLowerCase();
        return sources.stream()
            .filter(p -> p.name().toLowerCase().startsWith(lowerPrefix))
            .toList();
    }

    /**
     * Filters out SETTER kind for source completions.
     * Source mappings can only read from fields, getters, and parameters - not call setters.
     * This removes methods like addFirst, addLast, replaceAll, sort, etc. from source completions.
     */
    private List<FieldInfo> filterOutSetters(List<FieldInfo> fields) {
        return fields.stream()
            .filter(field -> field.kind() != FieldInfo.FieldKind.SETTER)
            .toList();
    }

    /**
     * Converts GETTER and FIELD kinds to SETTER for target completions.
     * This provides better UX by showing fields as "writable" when completing target attributes,
     * even for immutable classes that only have getters.
     *
     * PARAMETER kind is preserved (for multi-source mappers).
     * SETTER kind is already correct and unchanged.
     */
    private List<FieldInfo> convertToSetterKind(List<FieldInfo> fields) {
        return fields.stream()
            .map(field -> {
                // Convert GETTER and FIELD to SETTER for target context
                if (field.kind() == FieldInfo.FieldKind.GETTER || field.kind() == FieldInfo.FieldKind.FIELD) {
                    return new FieldInfo(field.name(), field.type(), FieldInfo.FieldKind.SETTER);
                }
                // Keep PARAMETER and SETTER unchanged
                return field;
            })
            .toList();
    }
}
