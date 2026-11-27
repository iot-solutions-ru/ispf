package com.ispf.server.datasource;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void blankSchemaIsRejectedBeforeUpdateOnInternal() {
        PlatformObject node = mock(PlatformObject.class);
        when(objectManager.require("root.platform.data-sources.schema-proof")).thenReturn(node);
        when(node.type()).thenReturn(ObjectType.DATA_SOURCE);
        when(node.getVariable("connectionMode")).thenReturn(Optional.empty());

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
        verify(objectManager, never()).updateInfo(any(), any(), any());
    }

    @Test
    void externalBlankSchemaPassesCreateValidation() {
        var request = new DataSourceObjectService.DataSourceWriteRequest(
                "ext-proof",
                "ext-proof",
                "external",
                "",
                "jdbc:postgresql://db.example/app",
                null,
                null,
                null,
                2,
                null
        );

        when(objectManager.create(any(), any(), any(), any(), any(), any()))
                .thenReturn(mock(PlatformObject.class));

        // Schema check must not 400; remaining null collaborators NPE after ObjectManager.create.
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(NullPointerException.class);
        verify(objectManager).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void externalBlankSchemaPassesUpdateValidation() {
        PlatformObject node = mock(PlatformObject.class);
        when(objectManager.require("root.platform.data-sources.ext-proof")).thenReturn(node);
        when(node.type()).thenReturn(ObjectType.DATA_SOURCE);
        when(node.getVariable(any())).thenReturn(Optional.empty());

        var request = new DataSourceObjectService.DataSourceWriteRequest(
                null,
                null,
                "external",
                "",
                null,
                null,
                null,
                null,
                null,
                null
        );

        // Schema check must not 400; null tenant guard NPEs next.
        assertThatThrownBy(() -> service.update("root.platform.data-sources.ext-proof", request))
                .isInstanceOf(NullPointerException.class);
        verify(objectManager).require("root.platform.data-sources.ext-proof");
        verify(objectManager, never()).updateInfo(any(), any(), any());
    }
}
