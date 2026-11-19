package com.ispf.server.api;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.application.function.FunctionAuditMode;
import com.ispf.server.application.function.FunctionInvokeAuditService;
import com.ispf.server.binding.BindingInvokeAuditService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformRuntimeAuditStatusMissingObjectTest {

    private static final String MISSING = "root.platform.devices.NO_SUCH";
    private static final String PRESENT = "root.platform.devices.present";

    private FunctionInvokeAuditService functionAuditService;
    private BindingInvokeAuditService bindingAuditService;
    private ObjectManager objectManager;
    private PlatformRuntimeController controller;

    @BeforeEach
    void setUp() {
        functionAuditService = mock(FunctionInvokeAuditService.class);
        bindingAuditService = mock(BindingInvokeAuditService.class);
        objectManager = mock(ObjectManager.class);
        when(functionAuditService.auditMode()).thenReturn(FunctionAuditMode.ERRORS);
        controller = new PlatformRuntimeController(functionAuditService, bindingAuditService, objectManager);
    }

    @Test
    void missingObjectIsNotADisabledAudit() {
        when(objectManager.require(MISSING)).thenThrow(new ObjectNotFoundException(MISSING));

        assertThatThrownBy(() -> controller.bindingAuditStatus(MISSING))
                .isInstanceOf(ObjectNotFoundException.class)
                .hasMessage("Object not found: " + MISSING);
        assertThatThrownBy(() -> controller.functionAuditStatus(MISSING))
                .isInstanceOf(ObjectNotFoundException.class)
                .hasMessage("Object not found: " + MISSING);
        verify(bindingAuditService, never()).isObjectAuditEnabled(MISSING);
        verify(functionAuditService, never()).isObjectAuditEnabled(MISSING);
    }

    @Test
    void existingObjectWithAuditOffStaysDisabled() {
        when(objectManager.require(PRESENT)).thenReturn(mock(PlatformObject.class));
        when(bindingAuditService.isMasterEnabled()).thenReturn(true);
        when(bindingAuditService.isObjectAuditEnabled(PRESENT)).thenReturn(false);
        when(functionAuditService.isMasterEnabled()).thenReturn(false);
        when(functionAuditService.isObjectAuditEnabled(PRESENT)).thenReturn(false);

        assertThat(controller.bindingAuditStatus(PRESENT))
                .containsEntry("objectEnabled", false)
                .containsEntry("enabled", false);
        assertThat(controller.functionAuditStatus(PRESENT))
                .containsEntry("objectEnabled", false)
                .containsEntry("enabled", false);
    }

    @Test
    void omittedPathDoesNotRequireAnObject() {
        when(bindingAuditService.isMasterEnabled()).thenReturn(true);
        when(functionAuditService.isMasterEnabled()).thenReturn(false);

        assertThat(controller.bindingAuditStatus(null)).containsEntry("enabled", true);
        assertThat(controller.functionAuditStatus("  ")).containsEntry("enabled", false);
        verify(objectManager, never()).require(org.mockito.ArgumentMatchers.any());
    }
}
