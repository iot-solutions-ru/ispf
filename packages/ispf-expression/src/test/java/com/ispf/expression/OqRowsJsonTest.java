package com.ispf.expression;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OqRowsJsonTest {

    @Test
    void encodesRowsAsJsonArray() {
        String json = OqRowsJson.encode(List.of(
                Map.of("path", "root.a", "count", 2),
                Map.of("path", "root.b", "name", "x\"y")
        ));
        assertThat(json).isEqualTo("[{\"path\":\"root.a\",\"count\":2},{\"path\":\"root.b\",\"name\":\"x\\\"y\"}]");
    }
}
