package com.ispf.server.api;

import com.ispf.server.mimic.MimicService;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MimicControllerBlankTitleTest {

    private static final String PATH = "root.platform.mimics.fresh";

    @Mock
    private MimicService mimicService;

    @Mock
    private VariableMemberAccessService variableMemberAccessService;

    @Test
    void blankTitleIsRejectedBeforeItIsSaved() {
        MimicController controller = controller();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> controller.saveTitle(PATH, new MimicController.SaveTitleRequest(" "), null)
        );

        assertEquals("title must not be blank", error.getMessage());
        verify(mimicService, never()).updateTitle(any(), any());
        verify(variableMemberAccessService, never()).requireWrite(anyString(), anyString(), any());
    }

    @Test
    void emptyTitleIsRejectedBeforeItIsSaved() {
        MimicController controller = controller();

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.saveTitle(PATH, new MimicController.SaveTitleRequest(""), null)
        );
        verify(mimicService, never()).updateTitle(any(), any());
    }

    @Test
    void aRealTitleIsSaved() {
        when(mimicService.updateTitle(PATH, "cc-wave-3321-t"))
                .thenReturn(new MimicService.MimicView(PATH, "cc-wave-3321-t", 5000, "{}"));
        MimicController controller = controller();

        MimicService.MimicView saved = controller.saveTitle(
                PATH,
                new MimicController.SaveTitleRequest("cc-wave-3321-t"),
                null
        );

        assertEquals("cc-wave-3321-t", saved.title());
        verify(mimicService).updateTitle(PATH, "cc-wave-3321-t");
    }

    private MimicController controller() {
        return new MimicController(mimicService, variableMemberAccessService);
    }
}
