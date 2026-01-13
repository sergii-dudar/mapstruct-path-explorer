package com.dsm.mapstruct.core.model;

import java.util.List;

/**
 * Result of path completion containing available fields and getters.
 */
public record CompletionResult(String className,
                               String simpleName,
                               String packageName,
                               String path,
                               List<FieldInfo> completions
) {

    /**
     * Creates an empty completion result for error cases.
     */
    public static CompletionResult empty(String className, String simpleName, String packageName, String path) {
        return new CompletionResult(className, simpleName, packageName, path, List.of());
    }

    /**
     * Creates a completion result with sorted completions.
     */
    public static CompletionResult of(String className, String simpleName, String packageName, String path, List<FieldInfo> completions) {
        var sortedCompletions = completions.stream()
                .sorted()
                .toList();
        return new CompletionResult(className, simpleName, packageName, path, sortedCompletions);
    }
}
