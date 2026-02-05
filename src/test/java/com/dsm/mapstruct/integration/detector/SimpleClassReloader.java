package com.dsm.mapstruct.integration.detector;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;

public class SimpleClassReloader {

    public static Class<?> loadFreshClass(String classPath, String className)
            throws Exception {
        URL[] urls = {new File(classPath).toURI().toURL()};
        URLClassLoader loader = new URLClassLoader(urls, null); // null parent
        return loader.loadClass(className);
    }

    public static void main(String[] args) throws Exception {
        String classPath = "/Users/iuada144/tools/java-extensions/mapstruct/mapstruct-path-explorer-repo/target/test-classes";
        // String className = "com.dsm.mapstruct.integration.dto.CollectionFirstDTO";
        String className = "com.dsm.mapstruct.testdata.TestClasses$Product";

        while (true) {
            // Load fresh version
            Class<?> clazz = loadFreshClass(classPath, className);

            System.out.println("\nFields:");
            for (Field f : clazz.getDeclaredFields()) {
                System.out.println("  " + f.getName());
            }

            Thread.sleep(5000); // Check every 5 seconds
        }
    }
}
