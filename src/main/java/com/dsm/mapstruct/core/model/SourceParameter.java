package com.dsm.mapstruct.core.model;

/**
 * Represents a source parameter in a mapper method.
 * Used for multi-parameter mappers where completion needs to resolve from specific parameters.
 * Example: CompletePersonDTO map(Person person, Order order, String customName)
 */
public record SourceParameter(
    String name,  // Parameter name (e.g., "person", "order")
    String type   // Fully qualified class name (e.g., "com.example.Person")
) {
    public SourceParameter {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Parameter name cannot be null or blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Parameter type cannot be null or blank");
        }
    }
}
