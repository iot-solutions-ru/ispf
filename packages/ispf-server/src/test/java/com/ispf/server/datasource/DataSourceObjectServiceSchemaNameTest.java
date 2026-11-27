package com.ispf.server.datasource;

import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DataSourceObjectServiceSchemaNameTest {

    private ObjectManager objectManager;
    private DataSourceObjectService service;

    @BeforeEach
    void setUp() {
        objectManager = mock(ObjectManager.class);
        service = new DataSourceObjectService(objectManager, null, null, null, null, null, null);
    }

    @Test
    void spacedSchemaIsRejectedBeforeSave() {
        var request = new DataSourceObjectService.DataSourceWriteRequest(
                "schema-proof",
                "schema-proof",
                "internal",
                "cc wave",
                null,
                null,
                null,
                null,
                2,
                null
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid schema");
        verify(objectManager, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void blankSchemaIsRejectedBeforeUpdate() {
        var request = new DataSourceObjectService.DataSourceWriteRequest(
                null,
                null,
                null,
                " ",
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.update("root.platform.data-sources.schema-proof", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid schema");
        verify(objectManager, never()).require(any());
    }
}
