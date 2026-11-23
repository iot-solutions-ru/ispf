package com.ispf.server.mimic;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MimicServiceInvalidDiagramTest {

    private static final String PATH = "root.platform.mimics.fresh";

    @Mock
    private ObjectManager objectManager;

    @Mock
    private SystemObjectStructureService structureService;

    @Mock
    private PlatformObject node;

    @Test
    void unreadableDiagramIsRejectedBeforeItIsSaved() {
        MimicService service = service();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveDiagram(PATH, "{")
        );

        assertEquals("Invalid diagramJson string", error.getMessage());
        verify(objectManager, never()).setVariableValue(any(), any(), any());
    }

    @Test
    void aJsonObjectIsSavedAsGiven() {
        when(objectManager.require(PATH)).thenReturn(node);
        when(node.type()).thenReturn(ObjectType.MIMIC);
        when(node.displayName()).thenReturn("fresh");
        when(node.getVariable(anyString())).thenReturn(Optional.empty());
        MimicService service = service();

        service.saveDiagram(PATH, "{\"widgets\":[]}");

        verify(objectManager).setVariableValue(eq(PATH), eq("diagram"), any());
    }

    private MimicService service() {
        return new MimicService(objectManager, structureService);
    }
}
