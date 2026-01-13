package com.dsm.mapstruct.util;

import com.dsm.mapstruct.model.FieldInfo;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility for matching field names against prefixes.
 */
public class NameMatcher {

    /**
     * Filters fields by prefix (case-insensitive).
     * If prefix is empty or null, returns all fields.
     *
     * @param fields the fields to filter
     * @param prefix the prefix to match (can be null or empty)
     * @return filtered list of fields matching the prefix
     */
    public List<FieldInfo> filterByPrefix(List<FieldInfo> fields, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return fields;
        }

        String lowerPrefix = prefix.toLowerCase();
        return fields.stream()
            .filter(field -> field.name().toLowerCase().startsWith(lowerPrefix))
            .collect(Collectors.toList());
    }

    /**
     * Checks if a name matches a prefix (case-insensitive).
     */
    public boolean matches(String name, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return true;
        }
        if (name == null) {
            return false;
        }
        return name.toLowerCase().startsWith(prefix.toLowerCase());
    }
}
