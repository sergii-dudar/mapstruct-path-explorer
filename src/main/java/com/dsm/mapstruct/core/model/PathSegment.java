package com.dsm.mapstruct.core.model;

/**
 * Represents a segment of a MapStruct path expression.
 * A segment can be a field access, method call, or collection operation.
 */
public record PathSegment(
    String name,
    SegmentType type,
    boolean hasParentheses
) {

    public enum SegmentType {
        FIELD,      // Direct field access: field1
        METHOD      // Method call: getFirst(), get(0)
    }

    /**
     * Creates a field segment.
     */
    public static PathSegment field(String name) {
        return new PathSegment(name, SegmentType.FIELD, false);
    }

    /**
     * Creates a method segment.
     */
    public static PathSegment method(String name) {
        return new PathSegment(name, SegmentType.METHOD, true);
    }

    /**
     * Checks if this segment is a partial prefix (doesn't end with parentheses or dot).
     */
    public boolean isPartial() {
        return !hasParentheses && !name.isEmpty();
    }

    @Override
    public String toString() {
        return type == SegmentType.METHOD ? name + "()" : name;
    }
}
