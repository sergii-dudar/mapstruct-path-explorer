package com.dsm.mapstruct.core.util;

import com.dsm.mapstruct.core.util.CollectionTypeResolverUtil;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Resolves generic type parameters from collection types.
 */
@UtilityClass
public class CollectionTypeResolverUtil {

    /**
     * Checks if a class is a collection type.
     */
    public static boolean isCollection(Class<?> clazz) {
        return clazz.isArray() ||
               Collection.class.isAssignableFrom(clazz) ||
               List.class.isAssignableFrom(clazz) ||
               Set.class.isAssignableFrom(clazz);
    }

    /**
     * Resolves the item type of a collection field.
     * For List<Person>, returns Person.class
     * For arrays, returns component type
     * For raw types, returns Object.class
     */
    public static Class<?> resolveCollectionItemType(Class<?> ownerClass, String fieldName) {
        try {
            // Try to find the field
            Field field = findField(ownerClass, fieldName);
            if (field != null) {
                return resolveCollectionItemType(field.getGenericType(), field.getType());
            }
        } catch (Exception e) {
            // Ignore and return null
        }
        return null;
    }

    /**
     * Resolves the item type of a collection from a method return type.
     */
    public Class<?> resolveCollectionItemTypeFromMethod(Method method) {
        return resolveCollectionItemType(method.getGenericReturnType(), method.getReturnType());
    }

    /**
     * Resolves the item type of a collection from a generic type.
     */
    private static Class<?> resolveCollectionItemType(Type genericType, Class<?> rawType) {
        // Handle arrays
        if (rawType.isArray()) {
            return rawType.getComponentType();
        }

        // Handle parameterized types (List<Person>, Set<String>, etc.)
        if (genericType instanceof ParameterizedType parameterizedType) {
            Type[] typeArgs = parameterizedType.getActualTypeArguments();
            if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?>) {
                return (Class<?>) typeArgs[0];
            }
        }

        // For raw types or unknown, return Object
        return Object.class;
    }

    /**
     * Checks if a method is a collection accessor method.
     * Note: These methods are available on List/Deque interfaces in Java 21+.
     * Arrays do NOT have these methods - they should be accessed via array indexing in MapStruct.
     *
     * Supported methods:
     * - getFirst() - List/Deque method (Java 21+)
     * - getLast() - List/Deque method (Java 21+)
     * - get(int) - List method
     * - first() - Some collection libraries
     * - last() - Some collection libraries
     */
    public static boolean isCollectionAccessor(String methodName) {
        return methodName.equals("getFirst") ||
               methodName.equals("getLast") ||
               methodName.equals("get") ||
               methodName.equals("first") ||
               methodName.equals("last");
    }

    /**
     * Checks if a property name is a MapStruct collection accessor property.
     * MapStruct uses property-style syntax for collection accessors:
     * - orders.first (not orders.getFirst())
     * - orders.last (not orders.getLast())
     * - items.empty (checks if empty)
     *
     * These are the property names that MapStruct recognizes for collections.
     */
    public static boolean isMapStructCollectionProperty(String propertyName) {
        return propertyName.equals("first") ||
               propertyName.equals("last") ||
               propertyName.equals("empty");
    }

    /**
     * Checks if a specific class type supports collection accessor methods.
     * Arrays do NOT support getFirst()/getLast() - only List and Deque interfaces do.
     */
    public static boolean supportsCollectionAccessors(Class<?> clazz) {
        // Arrays don't have getFirst/getLast methods
        if (clazz.isArray()) {
            return false;
        }
        // List and its implementations support these methods
        return List.class.isAssignableFrom(clazz);
    }

    /**
     * For collection accessor methods, resolves the return type considering generics.
     * For example, if we call getFirst() on List<Person>, returns Person.class
     */
    public static Class<?> resolveCollectionAccessorReturnType(Class<?> collectionType, String methodName, Field sourceField) {
        if (!isCollectionAccessor(methodName)) {
            return null;
        }

        // If we have the source field, extract generic type
        if (sourceField != null) {
            return resolveCollectionItemType(sourceField.getGenericType(), sourceField.getType());
        }

        // Otherwise, try to infer from collection type itself
        return Object.class;
    }

    /**
     * Finds a field in a class hierarchy.
     */
    private static Field findField(Class<?> clazz, String fieldName) {
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
}
