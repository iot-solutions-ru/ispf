package com.ispf.server.persistence;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectEntityMapperBlueprintJsonTest {

    private final ObjectEntityMapper mapper = new ObjectEntityMapper(new ObjectMapper());

    @Test
    void blankBlueprintJsonStaysEmpty() {
        assertEquals(0, mapper.readappliedBlueprintIds(null).size());
        assertEquals(0, mapper.readappliedBlueprintIds("  ").size());
        assertEquals(0, mapper.readBlueprintContributions(null).size());
        assertEquals(0, mapper.readBlueprintContributions("").size());
    }

    @Test
    void readableBlueprintJsonIsKept() {
        assertEquals(java.util.List.of("bp-1"), mapper.readappliedBlueprintIds("[\"bp-1\"]"));
        assertTrue(mapper.readBlueprintContributions("{}").isEmpty());
    }

    @Test
    void unreadableBlueprintJsonFailsWithParserText() {
        IllegalArgumentException ids = assertThrows(
                IllegalArgumentException.class,
                () -> mapper.readappliedBlueprintIds("{not-an-array")
        );
        assertTrue(ids.getMessage().startsWith("Invalid applied blueprint ids JSON:"));
        assertTrue(ids.getMessage().length() > "Invalid applied blueprint ids JSON:".length());

        IllegalArgumentException contributions = assertThrows(
                IllegalArgumentException.class,
                () -> mapper.readBlueprintContributions("[")
        );
        assertTrue(contributions.getMessage().startsWith("Invalid blueprint contributions JSON:"));
        assertTrue(contributions.getMessage().length() > "Invalid blueprint contributions JSON:".length());
    }
}
