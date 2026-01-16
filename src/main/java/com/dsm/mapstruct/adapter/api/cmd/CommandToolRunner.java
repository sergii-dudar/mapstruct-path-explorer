package com.dsm.mapstruct.adapter.api.cmd;

import com.dsm.mapstruct.core.model.SourceParameter;
import com.dsm.mapstruct.core.usecase.ExplorePathUseCase;

import java.util.List;

public class CommandToolRunner {

    /**
     * Runs the tool with the given arguments and returns an exit code.
     * This method is package-private to allow testing without calling System.exit().
     *
     * @param args command-line arguments
     * @return 0 for success, 1 for error
     */
    public static int run(String[] args) {
        if (args.length < 2) {
            printUsage();
            return 1;
        }

        String className = args[0];
        String pathExpression = args[1];

        try {
            ExplorePathUseCase explorePathUseCase = new ExplorePathUseCase();
            Class<?> clazz = Class.forName(className);

            // For command-line usage, wrap single class in a SourceParameter with default name "source"
            List<SourceParameter> sources = List.of(
                new SourceParameter("source", clazz.getName())
            );

            // Default to false for command-line usage (fields/getters, not enum constants)
            ExplorePathUseCase.ExplorePathParams params = new ExplorePathUseCase.ExplorePathParams(sources, pathExpression, false);
            System.out.println(explorePathUseCase.execute(params));
            return 0;
        } catch (ClassNotFoundException e) {
            printError("Class not found: " + className);
            System.err.println("Make sure the class is on the classpath.");
            System.err.println("\nExample:");
            System.err.println("  java -cp \"mapstruct-path-explorer.jar:path/to/your/classes\" \\");
            System.err.println("       com.dsm.mapstruct.MapStructPathTool \\");
            System.err.println("       \"" + className + "\" \"" + pathExpression + "\"");
            return 1;
        } catch (Exception e) {
            printError("Error processing path: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
    }

    private static void printUsage() {
        System.err.println("MapStruct Path Completion Tool");
        System.err.println();
        System.err.println("Usage:");
        System.err.println("  java -jar mapstruct-path-explorer.jar <fully.qualified.ClassName> <path.expression>");
        System.err.println();
        System.err.println("Examples:");
        System.err.println("  # Show all fields in address");
        System.err.println("  java -jar mapstruct-path-explorer.jar \"com.example.User\" \"address.\"");
        System.err.println();
        System.err.println("  # Show fields starting with 'str' in address");
        System.err.println("  java -jar mapstruct-path-explorer.jar \"com.example.User\" \"address.str\"");
        System.err.println();
        System.err.println("  # Navigate through collection");
        System.err.println("  java -jar mapstruct-path-explorer.jar \"com.example.Order\" \"items.getFirst().product.\"");
        System.err.println();
        System.err.println("  # Nested navigation");
        System.err.println("  java -jar mapstruct-path-explorer.jar \"com.example.Company\" \"employees.getFirst().address.city\"");
        System.err.println();
        System.err.println("With custom classpath:");
        System.err.println("  java -cp \"mapstruct-path-explorer.jar:path/to/classes\" \\");
        System.err.println("       com.dsm.mapstruct.MapStructPathTool \\");
        System.err.println("       \"com.example.User\" \"address.\"");
    }

    private static void printError(String message) {
        System.err.println("Error: " + message);
    }
}
