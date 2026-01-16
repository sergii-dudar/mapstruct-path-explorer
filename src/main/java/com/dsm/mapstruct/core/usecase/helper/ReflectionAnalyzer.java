package com.dsm.mapstruct.core.usecase.helper;

import com.dsm.mapstruct.core.model.FieldInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Analyzes Java classes using reflection to extract field and getter information.
 */
public class ReflectionAnalyzer {

    /**
     * Gets all accessible PUBLIC fields from a class, including inherited fields.
     * Only public fields can be used in MapStruct mappings.
     */
    public List<FieldInfo> getAllFields(Class<?> clazz) {
        List<FieldInfo> fields = new ArrayList<>();

        // Get all fields including inherited ones
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                // Skip synthetic, static, and non-public fields
                if (field.isSynthetic() ||
                    Modifier.isStatic(field.getModifiers()) ||
                    !Modifier.isPublic(field.getModifiers())) {
                    continue;
                }

                String typeName = getTypeName(field.getType());
                fields.add(new FieldInfo(field.getName(), typeName, FieldInfo.FieldKind.FIELD));
            }
            current = current.getSuperclass();
        }

        return fields;
    }

    /**
     * Gets all getter methods from a class.
     * A getter is a public method that:
     * - Starts with "get" or "is"
     * - Takes no parameters
     * - Returns a non-void value
     *
     * For Java records, also includes record component accessor methods:
     * - name() → name
     * - age() → age
     *
     * Names are transformed to MapStruct property format:
     * - getFirstName() → firstName
     * - isActive() → active
     */
    public List<FieldInfo> getAllGetters(Class<?> clazz) {
        List<FieldInfo> getters = new ArrayList<>();

        // For records, extract record component accessor methods
        if (clazz.isRecord()) {
            var recordComponents = clazz.getRecordComponents();
            if (recordComponents != null) {
                for (var component : recordComponents) {
                    String componentName = component.getName();
                    Class<?> componentType = component.getType();
                    String typeName = getTypeName(componentType);
                    getters.add(new FieldInfo(componentName, typeName, FieldInfo.FieldKind.GETTER));
                }
            }
        }

        // Regular getter methods
        for (Method method : clazz.getMethods()) {
            if (isGetter(method)) {
                String propertyName = getPropertyNameFromGetter(method.getName());

                // Filter out Object.getClass() - not useful for MapStruct mappings
                if (propertyName.equals("class")) {
                    continue;
                }

                String typeName = getTypeName(method.getReturnType());
                getters.add(new FieldInfo(propertyName, typeName, FieldInfo.FieldKind.GETTER));
            }
        }

        return getters;
    }

    /**
     * Gets enum constants for enum types.
     * Used for @ValueMapping completion.
     */
    public List<FieldInfo> getEnumConstants(Class<?> clazz) {
        if (!clazz.isEnum()) {
            return List.of();
        }

        List<FieldInfo> constants = new ArrayList<>();
        Object[] enumConstants = clazz.getEnumConstants();

        if (enumConstants != null) {
            for (Object constant : enumConstants) {
                Enum<?> enumConstant = (Enum<?>) constant;
                String name = enumConstant.name();
                String typeName = clazz.getSimpleName();
                // Use FIELD kind for enum constants (they are essentially static final fields)
                constants.add(new FieldInfo(name, typeName, FieldInfo.FieldKind.FIELD));
            }
        }

        return constants;
    }

    /**
     * Gets all setter methods from a class.
     * A setter is a public method that:
     * - Takes exactly 1 parameter
     * - Is not static
     * - Returns void OR returns the same class type (for fluent/builder pattern)
     *
     * Used for target completion on builder classes and POJOs with setters.
     * Examples:
     * - setFullName(String) → fullName (JavaBean style)
     * - fullName(String) → fullName (Builder/fluent style)
     */
    public List<FieldInfo> getAllSetters(Class<?> clazz) {
        List<FieldInfo> setters = new ArrayList<>();

        for (Method method : clazz.getMethods()) {
            if (isSetter(method, clazz)) {
                String propertyName = getPropertyNameFromSetter(method.getName());

                // Get the parameter type (first and only parameter)
                Class<?> paramType = method.getParameterTypes()[0];
                String typeName = getTypeName(paramType);

                setters.add(new FieldInfo(propertyName, typeName, FieldInfo.FieldKind.SETTER));
            }
        }

        return setters;
    }

    /**
     * Checks if a method is a setter method.
     */
    private boolean isSetter(Method method, Class<?> clazz) {
        // Must be public, non-static
        if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
            return false;
        }

        // Must take exactly 1 parameter
        if (method.getParameterCount() != 1) {
            return false;
        }

        // Return type must be void (JavaBean) or the class itself (fluent/builder)
        Class<?> returnType = method.getReturnType();
        if (returnType != void.class && returnType != clazz) {
            return false;
        }

        // Exclude common Object methods and utility methods
        String methodName = method.getName();
        if (methodName.equals("equals") || methodName.equals("wait") ||
            methodName.equals("notify") || methodName.equals("notifyAll") ||
            methodName.equals("toString")) {
            return false;
        }

        return true;
    }

    /**
     * Converts a setter method name to MapStruct property name format.
     * Examples:
     * - setFullName → fullName (JavaBean style)
     * - fullName → fullName (Builder/fluent style)
     */
    private String getPropertyNameFromSetter(String methodName) {
        if (methodName.startsWith("set") && methodName.length() > 3) {
            String propertyName = methodName.substring(3);
            return decapitalize(propertyName);
        }
        return methodName;
    }

    /**
     * Gets all fields and getters combined.
     * If both a field and a getter exist for the same property name,
     * only the getter is returned (MapStruct prefers getters).
     *
     * If no getters are found, also includes setters (for builder classes and target mappings).
     */
    public List<FieldInfo> getAllFieldsAndGetters(Class<?> clazz) {
        List<FieldInfo> fields = getAllFields(clazz);
        List<FieldInfo> getters = getAllGetters(clazz);
        List<FieldInfo> setters = getAllSetters(clazz);

        // Create a set of getter property names
        var getterNames = getters.stream()
                .map(FieldInfo::name)
                .collect(java.util.stream.Collectors.toSet());

        // Create a set of setter property names
        var setterNames = setters.stream()
                .map(FieldInfo::name)
                .collect(java.util.stream.Collectors.toSet());

        // Filter out fields that have a corresponding getter or setter
        var uniqueFields = fields.stream()
                .filter(field -> !getterNames.contains(field.name()) && !setterNames.contains(field.name()))
                .toList();

        // Combine: unique fields + all getters + all setters
        return Stream.concat(
            Stream.concat(uniqueFields.stream(), getters.stream()),
            setters.stream()
        ).toList();
    }

    /**
     * Finds a field or getter by name in the class.
     * Returns the type of the field/getter, or null if not found.
     * For records, also checks record component accessor methods.
     */
    public Class<?> getFieldOrGetterType(Class<?> clazz, String name) {
        // For records, check record components first
        if (clazz.isRecord()) {
            var recordComponents = clazz.getRecordComponents();
            if (recordComponents != null) {
                for (var component : recordComponents) {
                    if (component.getName().equals(name)) {
                        return component.getType();
                    }
                }
            }
        }

        // Try to find as field first
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                if (!Modifier.isStatic(field.getModifiers())) {
                    return field.getType();
                }
            } catch (NoSuchFieldException e) {
                // Continue to superclass
            }
            current = current.getSuperclass();
        }

        // Try to find as getter method
        String getterName = "get" + capitalize(name);
        String booleanGetterName = "is" + capitalize(name);

        for (Method method : clazz.getMethods()) {
            if ((method.getName().equals(name) ||
                 method.getName().equals(getterName) ||
                 method.getName().equals(booleanGetterName)) &&
                isGetter(method)) {
                return method.getReturnType();
            }
        }

        return null;
    }

    /**
     * Gets the return type of a method by name.
     */
    public Class<?> getMethodReturnType(Class<?> clazz, String methodName) {
        try {
            // Try common collection methods first
            if (methodName.equals("getFirst") || methodName.equals("getLast")) {
                // These methods are on List/Deque interfaces in Java 21
                Method method = findMethod(clazz, methodName);
                if (method != null) {
                    return method.getReturnType();
                }
            }

            if (methodName.equals("get")) {
                // List.get(int) method
                Method method = findMethod(clazz, methodName, int.class);
                if (method != null) {
                    return method.getReturnType();
                }
            }

            // Try to find any public method with this name and no parameters
            Method method = clazz.getMethod(methodName);
            return method.getReturnType();
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Checks if a method is a getter.
     */
    private boolean isGetter(Method method) {
        String name = method.getName();
        return Modifier.isPublic(method.getModifiers()) &&
               !Modifier.isStatic(method.getModifiers()) &&
               method.getParameterCount() == 0 &&
               method.getReturnType() != void.class &&
               (name.startsWith("get") || name.startsWith("is"));
    }

    /**
     * Finds a method by name and parameter types.
     */
    private Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            return clazz.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            // Check interfaces
            for (Class<?> iface : clazz.getInterfaces()) {
                Method method = findMethod(iface, name, parameterTypes);
                if (method != null) {
                    return method;
                }
            }
            return null;
        }
    }

    /**
     * Gets a readable type name for a class.
     */
    private String getTypeName(Class<?> clazz) {
        if (clazz.isArray()) {
            return getTypeName(clazz.getComponentType()) + "[]";
        }
        return clazz.getSimpleName();
    }

    /**
     * Capitalizes the first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Converts a getter method name to MapStruct property name format.
     * Examples:
     * - getFirstName → firstName
     * - isActive → active
     * - getURL → url (handles acronyms)
     */
    private String getPropertyNameFromGetter(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            String propertyName = methodName.substring(3);
            return decapitalize(propertyName);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            String propertyName = methodName.substring(2);
            return decapitalize(propertyName);
        }
        return methodName;
    }

    /**
     * Decapitalizes the first letter of a string, handling acronyms correctly.
     * Examples:
     * - FirstName → firstName
     * - URL → url
     * - XMLParser → xmlParser
     */
    private String decapitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        // If the string has more than one character and the second character is uppercase,
        // it's likely an acronym, so lowercase the entire prefix
        if (str.length() > 1 && Character.isUpperCase(str.charAt(1))) {
            // Find where the acronym ends
            int i = 0;
            while (i < str.length() && Character.isUpperCase(str.charAt(i))) {
                i++;
            }
            // If we reached the end or it's all uppercase, lowercase everything
            if (i == str.length()) {
                return str.toLowerCase();
            }
            // Otherwise, keep the last uppercase letter with the next part
            // e.g., "XMLParser" -> "xml" + "Parser" -> "xmlParser"
            if (i > 1) {
                return str.substring(0, i - 1).toLowerCase() + str.substring(i - 1);
            }
        }
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }
}
