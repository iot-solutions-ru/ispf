package com.ispf.server.eventfilter;

import com.ispf.server.eventfilter.EventFilterObjectService.EventFilterDefinition;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventFilterObjectServiceOversizedWindowTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private SystemObjectStructureService structureService;

    @Mock
    private EventFilterMatcher eventFilterMatcher;

    @Test
    void oversizedWindowIsRejectedBeforeSave() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().upsert(definition(999_999_999_999L))
        );

        assertEquals("timeWindowMs must not exceed " + Integer.MAX_VALUE, exception.getMessage());
        verify(objectManager, never()).create(any(), any(), any(), any(), any(), any());
        verify(objectManager, never()).setVariableValue(any(), any(), any());
    }

    private EventFilterObjectService service() {
        return new EventFilterObjectService(objectManager, structureService, eventFilterMatcher);
    }

    private static EventFilterDefinition definition(long timeWindowMs) {
        return new EventFilterDefinition(
                "",
                "window-proof",
                "window-proof",
                "proof",
                "*",
                "root.platform.**",
                0L,
                50L,
                timeWindowMs,
                "",
                false
        );
    }
}
