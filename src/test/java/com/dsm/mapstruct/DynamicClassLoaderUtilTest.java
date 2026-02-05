package com.dsm.mapstruct;

import com.dsm.mapstruct.core.util.DynamicClassLoaderUtil;
import com.dsm.mapstruct.testdata.TestClasses;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DynamicClassLoaderUtil to verify fresh ClassLoader behavior.
 */
class DynamicClassLoaderUtilTest {

    @Test
    void shouldCreateFreshClassLoader() {
        // Given: a class name to load
        String className = "com.dsm.mapstruct.testdata.TestClasses$Person";

        // When: creating a fresh ClassLoader
        ClassLoader classLoader = DynamicClassLoaderUtil.createFreshClassLoader(className);

        // Then: ClassLoader should not be null and should be a URLClassLoader
        assertThat(classLoader).isNotNull();
        assertThat(classLoader).isInstanceOf(java.net.URLClassLoader.class);
    }

    @Test
    void shouldLoadClassWithFreshClassLoader() {
        // Given: a class name and fresh ClassLoader
        String className = "com.dsm.mapstruct.testdata.TestClasses$Person";
        ClassLoader classLoader = DynamicClassLoaderUtil.createFreshClassLoader(className);

        // When: loading the class
        Class<?> loadedClass = DynamicClassLoaderUtil.loadClass(className, classLoader);

        // Then: class should be loaded successfully
        assertThat(loadedClass).isNotNull();
        assertThat(loadedClass.getName()).isEqualTo(className);
        assertThat(loadedClass.getSimpleName()).isEqualTo("Person");
    }

    @Test
    void shouldGetClassLocation() {
        // Given: a loaded class
        Class<?> clazz = TestClasses.Person.class;

        // When: getting its location
        String location = DynamicClassLoaderUtil.getClassLocation(clazz);

        // Then: location should point to a valid path
        assertThat(location).isNotNull();
        assertThat(location).contains("target"); // Should be in target/test-classes or similar
    }

    @Test
    void shouldCreateDifferentClassLoadersForSameClass() {
        // Given: the same class name
        String className = "com.dsm.mapstruct.testdata.TestClasses$Person";

        // When: creating two fresh ClassLoaders
        ClassLoader classLoader1 = DynamicClassLoaderUtil.createFreshClassLoader(className);
        ClassLoader classLoader2 = DynamicClassLoaderUtil.createFreshClassLoader(className);

        // Then: they should be different instances (not the same cached loader)
        assertThat(classLoader1).isNotNull();
        assertThat(classLoader2).isNotNull();
        assertThat(classLoader1).isNotSameAs(classLoader2);
    }

    @Test
    void shouldLoadDifferentClassInstancesFromDifferentLoaders() {
        // Given: the same class name
        String className = "com.dsm.mapstruct.testdata.TestClasses$Person";

        // When: loading with two different ClassLoaders
        ClassLoader classLoader1 = DynamicClassLoaderUtil.createFreshClassLoader(className);
        ClassLoader classLoader2 = DynamicClassLoaderUtil.createFreshClassLoader(className);

        Class<?> class1 = DynamicClassLoaderUtil.loadClass(className, classLoader1);
        Class<?> class2 = DynamicClassLoaderUtil.loadClass(className, classLoader2);

        // Then: classes should have same name but be different instances
        // (This proves they're loaded fresh, not cached)
        assertThat(class1.getName()).isEqualTo(class2.getName());
        assertThat(class1).isNotSameAs(class2);
    }

    @Test
    void shouldCreateClassLoaderForMultipleClasses() {
        // Given: multiple class names
        String[] classNames = {
            "com.dsm.mapstruct.testdata.TestClasses$Person",
            "com.dsm.mapstruct.testdata.TestClasses$Address",
            "com.dsm.mapstruct.testdata.TestClasses$Order"
        };

        // When: creating a ClassLoader for all of them
        ClassLoader classLoader = DynamicClassLoaderUtil.createFreshClassLoader(classNames);

        // Then: should be able to load all classes
        for (String className : classNames) {
            Class<?> loadedClass = DynamicClassLoaderUtil.loadClass(className, classLoader);
            assertThat(loadedClass).isNotNull();
            assertThat(loadedClass.getName()).isEqualTo(className);
        }
    }

    @Test
    void shouldHandleNullParentClassLoader() {
        // Given: a class to load
        String className = "com.dsm.mapstruct.testdata.TestClasses$Person";
        ClassLoader classLoader = DynamicClassLoaderUtil.createFreshClassLoader(className);

        // When: checking parent ClassLoader
        java.net.URLClassLoader urlClassLoader = (java.net.URLClassLoader) classLoader;

        // Then: parent should be null (to bypass system ClassLoader cache)
        assertThat(urlClassLoader.getParent()).isNull();
    }

    @Test
    void shouldExtractClasspathEntries() {
        // Given: a classpath string
        String classpath = "/path/to/classes:/path/to/lib.jar:/path/to/another.jar";

        // When: extracting entries
        String[] entries = DynamicClassLoaderUtil.extractClasspathEntries(classpath);

        // Then: should split correctly
        assertThat(entries).hasSize(3);
        assertThat(entries[0]).isEqualTo("/path/to/classes");
        assertThat(entries[1]).isEqualTo("/path/to/lib.jar");
        assertThat(entries[2]).isEqualTo("/path/to/another.jar");
    }

    @Test
    void shouldHandleEmptyClasspath() {
        // Given: empty classpath
        String classpath = "";

        // When: extracting entries
        String[] entries = DynamicClassLoaderUtil.extractClasspathEntries(classpath);

        // Then: should return empty array
        assertThat(entries).isEmpty();
    }
}
