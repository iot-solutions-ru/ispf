package com.ispf.server.report;

import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReportServiceInvalidLayoutTest {

    @Mock
    private ObjectManager objectManager;

    @Test
    void invalidLayoutIsRejectedBeforeSave() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().saveLayout("root.platform.reports.layout-proof", "{")
        );

        assertEquals("Invalid layout JSON", exception.getMessage());
        verify(objectManager, never()).require(any());
        verify(objectManager, never()).setVariableValue(any(), any(), any());
    }

    private ReportService service() {
        return new ReportService(
                objectManager,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper(),
                null,
                null,
                null
        );
    }
}
