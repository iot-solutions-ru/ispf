package com.ispf.server.api;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.server.mimic.MimicService;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MimicControllerStructureBeforeWriteTest {

    private static final String PATH = "root.platform.mimics.fresh";

    @Mock
    private MimicService mimicService;

    @Mock
    private VariableMemberAccessService variableMemberAccessService;

    @Test
    void saveDiagramCreatesTheStructureBeforeCheckingTheVariable() {
        when(mimicService.saveDiagram(eq(PATH), eq("{\"elements\":[]}")))
                .thenReturn(new MimicService.MimicView(PATH, "fresh", 5000, "{\"elements\":[]}"));
        MimicController controller = controller();

        controller.saveDiagram(PATH, new MimicController.SaveDiagramRequest("{\"elements\":[]}"), null);

        InOrder order = inOrder(mimicService, variableMemberAccessService);
        order.verify(mimicService).ensureMimicStructure(PATH);
        order.verify(variableMemberAccessService).requireWrite(PATH, "diagram", null);
        order.verify(mimicService).saveDiagram(PATH, "{\"elements\":[]}");
    }

    @Test
    void saveTitleCreatesTheStructureBeforeCheckingTheVariable() {
        when(mimicService.updateTitle(eq(PATH), eq("Pump room")))
                .thenReturn(new MimicService.MimicView(PATH, "Pump room", 5000, "{}"));
        MimicController controller = controller();

        controller.saveTitle(PATH, new MimicController.SaveTitleRequest("Pump room"), null);

        InOrder order = inOrder(mimicService, variableMemberAccessService);
        order.verify(mimicService).ensureMimicStructure(PATH);
        order.verify(variableMemberAccessService).requireWrite(PATH, "title", null);
        order.verify(mimicService).updateTitle(PATH, "Pump room");
    }

    @Test
    void missingObjectStaysNotFoundBeforeTheVariableCheck() {
        doThrow(new ObjectNotFoundException(PATH)).when(mimicService).ensureMimicStructure(PATH);
        MimicController controller = controller();

        assertThrows(
                ObjectNotFoundException.class,
                () -> controller.saveDiagram(PATH, new MimicController.SaveDiagramRequest("{\"elements\":[]}"), null)
        );
        verify(variableMemberAccessService, never()).requireWrite(anyString(), anyString(), any());
    }

    @Test
    void variableCheckStillRejectsAMissingVariable() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Variable: diagram"))
                .when(variableMemberAccessService)
                .requireWrite(PATH, "diagram", null);
        MimicController controller = controller();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.saveDiagram(PATH, new MimicController.SaveDiagramRequest("{\"elements\":[]}"), null)
        );
        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(mimicService).ensureMimicStructure(PATH);
        verify(mimicService, never()).saveDiagram(any(), any());
    }

    private MimicController controller() {
        return new MimicController(mimicService, variableMemberAccessService);
    }
}
