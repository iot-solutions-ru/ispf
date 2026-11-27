package com.ispf.server.binding;

import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SqlBindingObjectServiceNegativeIntervalTest {

    private final ObjectManager objectManager = mock(ObjectManager.class);
    private final SqlBindingObjectService service = new SqlBindingObjectService(
            objectManager, null, null, null, null);

    @Test
    void negativeIntervalIsRejectedBeforeWrite() {
        assertRejected(-1L);
    }

    @Test
    void zeroIntervalIsRejectedBeforeWrite() {
        assertRejected(0L);
    }

    @Test
    void oversizedIntervalIsRejectedBeforeWrite() {
        IllegalArgumentException created = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(definition(3_000_000_000L))
        );
        IllegalArgumentException updated = assertThrows(
                IllegalArgumentException.class,
                () -> service.update("root.platform.bindings.interval-proof", definition(3_000_000_000L))
        );
        assertEquals("refreshIntervalMs must not exceed 2147483647", created.getMessage());
        assertEquals("refreshIntervalMs must not exceed 2147483647", updated.getMessage());
        verifyNoInteractions(objectManager);
    }

    private void assertRejected(long refreshIntervalMs) {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(definition(refreshIntervalMs))
        );
        assertEquals("refreshIntervalMs must be positive", ex.getMessage());
        verifyNoInteractions(objectManager);
    }

    private static SqlBindingObjectService.BindingDefinition definition(long refreshIntervalMs) {
        return new SqlBindingObjectService.BindingDefinition(
                "root.platform.bindings.interval-proof",
                "interval-proof",
                "root.platform.devices.interval-proof",
                "value",
                "",
                "SELECT 1 AS value",
                "value",
                "manual",
                refreshIntervalMs,
                "",
                "",
                false,
                null
        );
    }
}
