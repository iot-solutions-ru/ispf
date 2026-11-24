package com.ispf.server.report;

import com.ispf.server.object.ObjectManager;
import com.ispf.server.report.ReportService.SaveTreeVariablesDefinitionRequest;
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
class ReportServiceNegativeRefreshTest {

    @Mock
    private ObjectManager objectManager;

    @Test
    void nonPositiveRefreshIsRejectedBeforeSave() {
        ReportService service = service();

        IllegalArgumentException negative = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveTreeVariablesDefinition(
                        "root.platform.reports.refresh-proof",
                        request(-1)
                )
        );
        IllegalArgumentException zero = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveTreeVariablesDefinition(
                        "root.platform.reports.refresh-proof",
                        request(0)
                )
        );

        assertEquals("refreshIntervalMs must be positive", negative.getMessage());
        assertEquals("refreshIntervalMs must be positive", zero.getMessage());
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
                null,
                null,
                null,
                null
        );
    }

    private static SaveTreeVariablesDefinitionRequest request(int refreshIntervalMs) {
        return new SaveTreeVariablesDefinitionRequest(
                "refresh-proof",
                "root.platform.devices.**",
                "value",
                null,
                10,
                refreshIntervalMs
        );
    }
}
