package com.ispf.server.object;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.ObjectTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BindingRulesStartupRunnerGateTest {

    @Mock
    ObjectManager objectManager;
    @Mock
    BindingDependencyIndex dependencyIndex;
    @Mock
    BindingRuleEngine bindingRuleEngine;
    @Mock
    BindingRulesService bindingRulesService;
    @Mock
    BindingPeriodicScheduleRegistry periodicScheduleRegistry;
    @Mock
    BindingPeriodicScheduler periodicScheduler;

    private BindingRulesStartupRunner runner;

    @BeforeEach
    void setUp() {
        runner = new BindingRulesStartupRunner(
                objectManager,
                dependencyIndex,
                bindingRuleEngine,
                bindingRulesService,
                periodicScheduleRegistry,
                periodicScheduler
        );
    }

    @Test
    void initializeSkipsWhenObjectTreeNotReady() {
        when(objectManager.isInitialized()).thenReturn(false);

        runner.initializeBindingRules();

        verify(dependencyIndex, never()).rebuildAll(any());
        verify(periodicScheduleRegistry, never()).clearAll();
        verify(periodicScheduler, never()).reschedule();
        verify(bindingRuleEngine, never()).onStartup(any());
    }

    @Test
    void initializeContinuesWhenABindingObjectIsMissing() {
        when(objectManager.isInitialized()).thenReturn(true);
        when(objectManager.tree()).thenReturn(new ObjectTree());
        when(periodicScheduleRegistry.objectPathsWithBindingRules()).thenReturn(List.of("root.missing"));
        when(bindingRulesService.listRules("root.missing")).thenThrow(new ObjectNotFoundException("root.missing"));

        runner.initializeBindingRules();

        verify(periodicScheduleRegistry).clearAll();
        verify(periodicScheduler).reschedule();
        verify(periodicScheduleRegistry, never()).syncObject(any(), any());
    }

    @Test
    void initializeContinuesWhenDependencyRebuildMissesAnObject() {
        when(objectManager.isInitialized()).thenReturn(true);
        when(objectManager.tree()).thenReturn(new ObjectTree());
        when(periodicScheduleRegistry.objectPathsWithBindingRules()).thenReturn(List.of());
        doThrow(new ObjectNotFoundException("root")).when(dependencyIndex).rebuild(anyString());

        runner.initializeBindingRules();

        verify(dependencyIndex).rebuildAll(List.of());
        verify(dependencyIndex).rebuild("root");
        verify(periodicScheduleRegistry).clearAll();
        verify(periodicScheduler).reschedule();
    }
}
