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
class EventFilterObjectServiceInvertedSeverityTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private SystemObjectStructureService structureService;

    @Mock
    private EventFilterMatcher eventFilterMatcher;

    @Test
    void severityOutsideRangeIsRejectedBeforeSave() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().upsert(definition(500, -5))
        );

        assertEquals("minSeverity must be between 0 and 100", exception.getMessage());
        verify(objectManager, never()).create(any(), any(), any(), any(), any(), any());
        verify(objectManager, never()).setVariableValue(any(), any(), any());
    }

    @Test
    void invertedSeverityIsRejectedBeforeSave() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().upsert(definition(80, 20))
        );

        assertEquals("minSeverity must not be greater than maxSeverity", exception.getMessage());
        verify(objectManager, never()).create(any(), any(), any(), any(), any(), any());
    }

    private EventFilterObjectService service() {
        return new EventFilterObjectService(objectManager, structureService, eventFilterMatcher);
    }

    private static EventFilterDefinition definition(long minSeverity, long maxSeverity) {
        return new EventFilterDefinition(
                "",
                "severity-proof",
                "Severity",
                "",
                "probe",
                "root.platform.**",
                minSeverity,
                maxSeverity,
                0L,
                "",
                false
        );
    }
}
