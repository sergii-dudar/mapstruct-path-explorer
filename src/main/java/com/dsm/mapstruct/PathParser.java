package com.dsm.mapstruct;

import com.dsm.mapstruct.model.PathSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses MapStruct path expressions into navigable segments.
 *
 * Examples:
 * - "field1.field2" -> [field(field1), field(field2)]
 * - "items.getFirst().name" -> [field(items), method(getFirst), field(name)]
 * - "address." -> [field(address), field("")]
 * - "address.str" -> [field(address), field(str)]
 */
public class PathParser {

    /**
     * Parses a path expression into segments.
     *
     * @param path the path expression to parse
     * @return list of path segments
     */
    public List<PathSegment> parse(String path) {
        if (path == null || path.isEmpty()) {
            return List.of();
        }

        List<PathSegment> segments = new ArrayList<>();
        StringBuilder currentSegment = new StringBuilder();
        int parenthesesDepth = 0;

        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);

            if (c == '(') {
                parenthesesDepth++;
                currentSegment.append(c);
            } else if (c == ')') {
                parenthesesDepth--;
                currentSegment.append(c);

                // If we've closed all parentheses, this segment is a method call
                if (parenthesesDepth == 0) {
                    String segmentName = extractMethodName(currentSegment.toString());
                    if (!segmentName.isEmpty()) {
                        segments.add(PathSegment.method(segmentName));
                    }
                    currentSegment = new StringBuilder();
                }
            } else if (c == '.' && parenthesesDepth == 0) {
                // Dot outside parentheses - end of current segment
                String segmentName = currentSegment.toString().trim();
                if (!segmentName.isEmpty()) {
                    segments.add(PathSegment.field(segmentName));
                }
                currentSegment = new StringBuilder();
            } else {
                currentSegment.append(c);
            }
        }

        // Handle remaining segment (could be empty for trailing dot, or a partial name)
        String remaining = currentSegment.toString().trim();
        if (!remaining.isEmpty() || (path.endsWith(".") && !segments.isEmpty())) {
            // Add as field segment (could be partial)
            segments.add(PathSegment.field(remaining));
        }

        return segments;
    }

    /**
     * Extracts method name from a method call string like "getFirst()" or "get(0)".
     */
    private String extractMethodName(String methodCall) {
        int openParen = methodCall.indexOf('(');
        if (openParen > 0) {
            return methodCall.substring(0, openParen).trim();
        }
        return methodCall.trim();
    }

    /**
     * Checks if the path ends with a partial segment (no trailing dot or parentheses).
     * This is used to determine if we should filter by prefix.
     */
    public boolean hasPartialSegment(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        // If path ends with dot or closing paren, it's complete
        char lastChar = path.charAt(path.length() - 1);
        return lastChar != '.' && lastChar != ')';
    }

    /**
     * Gets the last segment if it's partial, empty otherwise.
     */
    public String getPartialSegment(List<PathSegment> segments) {
        if (segments.isEmpty()) {
            return "";
        }

        PathSegment last = segments.get(segments.size() - 1);
        if (last.type() == PathSegment.SegmentType.FIELD && !last.name().isEmpty()) {
            return last.name();
        }

        return "";
    }
}
