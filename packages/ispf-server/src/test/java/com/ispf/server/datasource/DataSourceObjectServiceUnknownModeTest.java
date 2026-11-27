package com.ispf.server.datasource;

import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DataSourceObjectServiceUnknownModeTest {

    private ObjectManager objectManager;
    private DataSourceObjectService service;

    @BeforeEach
    void setUp() {
        objectManager = mock(ObjectManager.class);
        service = new DataSourceObjectService(objectManager, null, null, null, null, null, null);
    }

    @Test
    void unknownModeIsRejectedBeforeSave() {
        var request = new DataSourceObjectService.DataSourceWriteRequest(
                "mode-proof",
                "mode-proof",
                "NO_SUCH",
                "mode_proof",
                null,
                null,
                null,
                null,
                2,
                null
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("connectionMode must be internal or external");
        verify(objectManager, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void blankModeIsRejectedBeforeSave() {
        var request = new DataSourceObjectService.DataSourceWriteRequest(
                "mode-proof",
                "mode-proof",
                " ",
                "mode_proof",
                null,
                null,
                null,
                null,
                2,
                null
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("connectionMode must be internal or external");
        verify(objectManager, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void unknownModeIsRejectedBeforeUpdate() {
        var request = new DataSourceObjectService.DataSourceWriteRequest(
                null,
                null,
                "NO_SUCH",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.update("root.platform.data-sources.mode-proof", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("connectionMode must be internal or external");
        verify(objectManager, never()).require(any());
    }
}
