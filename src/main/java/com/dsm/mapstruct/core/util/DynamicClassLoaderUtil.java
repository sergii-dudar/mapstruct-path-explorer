package com.dsm.mapstruct.core.util;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility for creating fresh ClassLoaders that reload classes from disk.
 * This solves the problem where recompiled classes are not visible because
 * they're cached by the system ClassLoader.
 *
 * Strategy:
 * 1. For each class we need to load, find its source location (directory or JAR)
 * 2. Collect all unique source locations
 * 3. Create a new URLClassLoader with null parent (bypasses system ClassLoader cache)
 * 4. Load classes from this fresh ClassLoader
 */
@Slf4j
public class DynamicClassLoaderUtil {

    /**
     * Creates a fresh ClassLoader that can reload classes from disk.
     * Uses null parent to bypass system ClassLoader caching.
     *
     * @param classNames fully qualified class names that will be loaded
     * @return a new URLClassLoader with all necessary classpath entries
     */
    public static ClassLoader createFreshClassLoader(String... classNames) {
        Set<URL> urls = new HashSet<>();

        // Collect source locations for all classes
        for (String className : classNames) {
            try {
                // First load with system ClassLoader to find location
                Class<?> clazz = Class.forName(className);
                String location = getClassLocation(clazz);

                if (location != null) {
                    URL url = new File(location).toURI().toURL();
                    urls.add(url);
                    log.debug("Added to classpath: {} for class {}", location, className);
                }
            } catch (ClassNotFoundException e) {
                log.warn("Class not found when building classpath: {}", className);
            } catch (Exception e) {
                log.warn("Error getting location for class {}: {}", className, e.getMessage());
            }
        }

        if (urls.isEmpty()) {
            log.warn("No classpath URLs found, using system ClassLoader");
            return ClassLoader.getSystemClassLoader();
        }

        // Create URLClassLoader with null parent to bypass caching
        URL[] urlArray = urls.toArray(new URL[0]);

        log.debug("Creating fresh ClassLoader with {} URLs", urlArray.length);
        for (URL url : urlArray) {
            log.debug("  - {}", url);
        }

        // Use null parent to force loading from our URLs without caching
        return new URLClassLoader(urlArray, null);
    }

    /**
     * Gets the file system location (directory or JAR) where a class is loaded from.
     *
     * @param clazz the class to locate
     * @return file path to the directory or JAR, or null if not found
     */
    @SneakyThrows
    public static String getClassLocation(Class<?> clazz) {
        var cs = clazz.getProtectionDomain().getCodeSource();
        if (cs == null) {
            // Bootstrap classes (java.lang.* etc.) sometimes return null
            return null;
        }
        var url = cs.getLocation();
        return Paths.get(url.toURI()).toString();
    }

    /**
     * Loads a class using the provided ClassLoader.
     * Wraps ClassNotFoundException in RuntimeException for cleaner code flow.
     *
     * @param className   fully qualified class name
     * @param classLoader the ClassLoader to use
     * @return the loaded Class
     * @throws RuntimeException if class not found
     */
    @SneakyThrows
    public static Class<?> loadClass(String className, ClassLoader classLoader) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            log.error("Class not found with custom ClassLoader: {}", className, e);
            throw new RuntimeException("Class not found: " + className, e);
        }
    }

    /**
     * Extracts all unique class names from a classpath string.
     * This is useful when you need to build a ClassLoader from a classpath.
     *
     * @param classpath colon-separated classpath string
     * @return array of classpath entries (directories and JARs)
     */
    public static String[] extractClasspathEntries(String classpath) {
        if (classpath == null || classpath.isEmpty()) {
            return new String[0];
        }

        return classpath.split(":");
    }

    /**
     * Creates a ClassLoader from explicit classpath entries.
     * Useful when you already know the directories/JARs to include.
     *
     * @param classpathEntries paths to directories or JAR files
     * @return a new URLClassLoader
     */
    @SneakyThrows
    public static ClassLoader createClassLoaderFromPaths(String... classpathEntries) {
        Set<URL> urls = new HashSet<>();

        for (String entry : classpathEntries) {
            File file = new File(entry);
            if (file.exists()) {
                urls.add(file.toURI().toURL());
                log.debug("Added to classpath: {}", entry);
            } else {
                log.warn("Classpath entry does not exist: {}", entry);
            }
        }

        if (urls.isEmpty()) {
            log.warn("No valid classpath entries found");
            return ClassLoader.getSystemClassLoader();
        }

        URL[] urlArray = urls.toArray(new URL[0]);
        log.debug("Creating ClassLoader with {} classpath entries", urlArray.length);

        // Use null parent to bypass system ClassLoader caching
        return new URLClassLoader(urlArray, null);
    }
}
