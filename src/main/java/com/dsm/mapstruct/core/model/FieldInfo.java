package com.dsm.mapstruct.core.model;

/**
 * Information about a field or getter method available for completion.
 */
public record FieldInfo(
    String name,
    String type,
    FieldKind kind
) implements Comparable<FieldInfo> {

    public enum FieldKind {
        FIELD,
        GETTER,
        PARAMETER  // Method parameter (for multi-source mappers)
    }

    @Override
    public int compareTo(FieldInfo other) {
        // Sort by name alphabetically
        return this.name.compareTo(other.name);
    }

    @Override
    public String toString() {
        return String.format("%s: %s (%s)", name, type, kind);
    }
}
