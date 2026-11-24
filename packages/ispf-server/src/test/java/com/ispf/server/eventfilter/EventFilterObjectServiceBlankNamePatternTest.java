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
class EventFilterObjectServiceBlankNamePatternTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private SystemObjectStructureService structureService;

    @Mock
    private EventFilterMatcher eventFilterMatcher;

    @Test
    void blankNamePatternIsRejectedBeforeSave() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().upsert(definition(" "))
        );

        assertEquals("eventNamePattern must not be blank", exception.getMessage());
        verify(objectManager, never()).create(any(), any(), any(), any(), any(), any());
        verify(objectManager, never()).setVariableValue(any(), any(), any());
    }

    private EventFilterObjectService service() {
        return new EventFilterObjectService(objectManager, structureService, eventFilterMatcher);
    }

    private static EventFilterDefinition definition(String eventNamePattern) {
        return new EventFilterDefinition(
                "",
                "pattern-proof",
                "Pattern",
                "",
                eventNamePattern,
                "root.platform.**",
                0L,
                100L,
                0L,
                "",
                false
        );
    }
}
