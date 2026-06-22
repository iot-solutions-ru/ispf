package com.ispf.server.application.catalog;

import com.ispf.server.api.dto.DataRecordPayloadRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventCatalogPayloadValidatorTest {

    @Mock
    private ApplicationEventCatalogStore store;

    private EventCatalogPayloadValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EventCatalogPayloadValidator(store, new ObjectMapper());
    }

    @Test
    void acceptsPayloadMatchingCatalogSchema() {
        when(store.find("mes-reference", "mesOrderUpdated")).thenReturn(Optional.of(entry("""
                {
                  "type": "object",
                  "required": ["orderNo"],
                  "properties": {
                    "orderNo": { "type": "string" },
                    "liters": { "type": "number" }
                  }
                }
                """)));

        assertDoesNotThrow(() -> validator.validateAtFire(
                "mes-reference",
                "mesOrderUpdated",
                new DataRecordPayloadRequest(null, List.of(Map.of("orderNo", "DO-1001", "liters", 42)))
        ));
    }

    @Test
    void rejectsMissingRequiredField() {
        when(store.find("mes-reference", "mesOrderUpdated")).thenReturn(Optional.of(entry("""
                {
                  "type": "object",
                  "required": ["orderNo"],
                  "properties": { "orderNo": { "type": "string" } }
                }
                """)));

        assertThrows(IllegalArgumentException.class, () -> validator.validateAtFire(
                "mes-reference",
                "mesOrderUpdated",
                new DataRecordPayloadRequest(null, List.of(Map.of()))
        ));
    }

    @Test
    void rejectsWrongPropertyType() {
        when(store.find("mes-reference", "mesOrderUpdated")).thenReturn(Optional.of(entry("""
                {
                  "type": "object",
                  "properties": { "orderNo": { "type": "string" } }
                }
                """)));

        assertThrows(IllegalArgumentException.class, () -> validator.validateAtFire(
                "mes-reference",
                "mesOrderUpdated",
                new DataRecordPayloadRequest(null, List.of(Map.of("orderNo", 1001)))
        ));
    }

    private static ApplicationEventCatalogStore.EventCatalogEntry entry(String schemaJson) {
        return new ApplicationEventCatalogStore.EventCatalogEntry("mesOrderUpdated", "[]", schemaJson);
    }
}
