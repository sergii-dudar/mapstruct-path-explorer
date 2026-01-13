package com.dsm.mapstruct;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import com.dsm.mapstruct.model.PathSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class PathParserTest {

    PathParser parser = new PathParser();

    @Test
    void testParseSimpleFieldPath() {
        List<PathSegment> segments = parser.parse("field1.field2");

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).name()).isEqualTo("field1");
        assertThat(segments.get(0).type()).isEqualTo(PathSegment.SegmentType.FIELD);
        assertThat(segments.get(1).name()).isEqualTo("field2");
        assertThat(segments.get(1).type()).isEqualTo(PathSegment.SegmentType.FIELD);
    }

    @Test
    void testParsePathWithTrailingDot() {
        List<PathSegment> segments = parser.parse("address.");

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).name()).isEqualTo("address");
        assertThat(segments.get(1).name()).isEmpty();
    }

    @Test
    void testParsePathWithMethodCall() {
        List<PathSegment> segments = parser.parse("items.getFirst().name");

        assertThat(segments).hasSize(3);
        assertThat(segments.get(0).name()).isEqualTo("items");
        assertThat(segments.get(0).type()).isEqualTo(PathSegment.SegmentType.FIELD);
        assertThat(segments.get(1).name()).isEqualTo("getFirst");
        assertThat(segments.get(1).type()).isEqualTo(PathSegment.SegmentType.METHOD);
        assertThat(segments.get(2).name()).isEqualTo("name");
        assertThat(segments.get(2).type()).isEqualTo(PathSegment.SegmentType.FIELD);
    }

    @Test
    void testParsePathWithMethodParameter() {
        List<PathSegment> segments = parser.parse("list.get(0).value");

        assertThat(segments).hasSize(3);
        assertThat(segments.get(1).name()).isEqualTo("get");
        assertThat(segments.get(1).type()).isEqualTo(PathSegment.SegmentType.METHOD);
    }

    @Test
    void testParsePartialPath() {
        List<PathSegment> segments = parser.parse("address.str");

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).name()).isEqualTo("address");
        assertThat(segments.get(1).name()).isEqualTo("str");
    }

    @Test
    void testParseEmptyPath() {
        List<PathSegment> segments = parser.parse("");

        assertThat(segments).isEmpty();
    }

    @Test
    void testParseNullPath() {
        List<PathSegment> segments = parser.parse(null);

        assertThat(segments).isEmpty();
    }

    @Test
    void testHasPartialSegment() {
        assertThat(parser.hasPartialSegment("address.str")).isTrue();
        assertThat(parser.hasPartialSegment("address.")).isFalse();
        assertThat(parser.hasPartialSegment("items.getFirst()")).isFalse();
        assertThat(parser.hasPartialSegment("")).isFalse();
    }

    @Test
    void testGetPartialSegment() {
        List<PathSegment> segments1 = parser.parse("address.str");
        assertThat(parser.getPartialSegment(segments1)).isEqualTo("str");

        List<PathSegment> segments2 = parser.parse("address.");
        assertThat(parser.getPartialSegment(segments2)).isEmpty();

        List<PathSegment> segments3 = parser.parse("items.getFirst()");
        assertThat(parser.getPartialSegment(segments3)).isEmpty();
    }
}
