package com.ispf.server.alert;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleListenerGateTest {

    @Mock
    AlertRuleService alertRuleService;
    @Mock
    ObjectManager objectManager;

    private AlertRuleListener listener;

    @BeforeEach
    void setUp() {
        listener = new AlertRuleListener(alertRuleService, objectManager);
    }

    @Test
    void handleSkipsWhenObjectTreeNotReady() {
        when(objectManager.isInitialized()).thenReturn(false);

        listener.handle(ObjectChangeEvent.variableUpdated("root.platform.devices.demo", "temperature"));

        verify(alertRuleService, never()).processVariableChange(any(), any());
    }

    @Test
    void handleProcessesWhenObjectTreeReady() {
        when(objectManager.isInitialized()).thenReturn(true);

        listener.handle(ObjectChangeEvent.variableUpdated("root.platform.devices.demo", "temperature"));

        verify(alertRuleService).processVariableChange("root.platform.devices.demo", "temperature");
    }
}
