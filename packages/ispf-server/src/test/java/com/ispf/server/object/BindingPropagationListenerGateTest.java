package com.ispf.server.object;

import com.ispf.server.config.ObjectChangeProperties;
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
class BindingPropagationListenerGateTest {

    @Mock
    BindingRuleEngine bindingRuleEngine;
    @Mock
    BindingDependencyIndex dependencyIndex;
    @Mock
    ObjectChangeProperties objectChangeProperties;
    @Mock
    ObjectManager objectManager;

    private BindingPropagationListener listener;

    @BeforeEach
    void setUp() {
        listener = new BindingPropagationListener(
                bindingRuleEngine,
                dependencyIndex,
                objectChangeProperties,
                objectManager
        );
    }

    @Test
    void onObjectChangeSkipsWhenObjectTreeNotReady() {
        when(objectManager.isInitialized()).thenReturn(false);

        listener.onObjectChange(ObjectChangeEvent.eventFired("root.device", "trip"));
        listener.onObjectChange(ObjectChangeEvent.variableUpdated("root.device", "temperature"));

        verify(bindingRuleEngine, never()).onEvent(any(), any());
        verify(bindingRuleEngine, never()).onVariableChanged(any(), any(), any());
        verify(bindingRuleEngine, never()).onContextChange(any());
    }
}
