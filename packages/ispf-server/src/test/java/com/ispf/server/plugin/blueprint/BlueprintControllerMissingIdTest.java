package com.ispf.server.plugin.blueprint;

import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintException;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.formula.AnalyticsFormulaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlueprintControllerMissingIdTest {

    private static final String ID = "00000000-0000-0000-0000-000000000099";

    @Mock
    private BlueprintRegistry blueprintRegistry;
    @Mock
    private BlueprintEngine blueprintEngine;
    @Mock
    private ObjectManager objectManager;
    @Mock
    private BlueprintPersistenceService blueprintPersistence;
    @Mock
    private BlueprintMergeService blueprintMergeService;
    @Mock
    private BlueprintApplicationService blueprintApplicationService;
    @Mock
    private AnalyticsFormulaService analyticsFormulaService;
    @Mock
    private BlueprintAnalyticsFormulaSupport blueprintAnalyticsFormulaSupport;
    @Mock
    private BlueprintBindingRulesMerger blueprintBindingRulesMerger;

    private BlueprintController controller;

    @BeforeEach
    void setUp() {
        controller = new BlueprintController(
                blueprintRegistry,
                blueprintEngine,
                objectManager,
                blueprintPersistence,
                blueprintMergeService,
                blueprintApplicationService,
                analyticsFormulaService,
                blueprintAnalyticsFormulaSupport,
                blueprintBindingRulesMerger
        );
    }

    @Test
    void missingIdIsNotFoundOnGetInstancesAndDiff() {
        when(blueprintRegistry.requireById(ID)).thenThrow(new BlueprintException("Model not found: " + ID));

        ResponseStatusException get = assertThrows(ResponseStatusException.class, () -> controller.get(ID));
        ResponseStatusException instances = assertThrows(
                ResponseStatusException.class,
                () -> controller.listInstances(ID)
        );
        ResponseStatusException diff = assertThrows(
                ResponseStatusException.class,
                () -> controller.diff(ID, "root.platform.devices.room")
        );

        assertEquals(HttpStatus.NOT_FOUND, get.getStatusCode());
        assertEquals("Blueprint not found: " + ID, get.getReason());
        assertEquals(HttpStatus.NOT_FOUND, instances.getStatusCode());
        assertEquals("Blueprint not found: " + ID, instances.getReason());
        assertEquals(HttpStatus.NOT_FOUND, diff.getStatusCode());
        assertEquals("Blueprint not found: " + ID, diff.getReason());
        verify(objectManager, never()).require("root.platform.devices.room");
    }

    @Test
    void otherBlueprintErrorsStayBlueprintExceptions() {
        when(blueprintRegistry.requireById(ID)).thenThrow(new BlueprintException("Blueprint is locked"));

        assertThrows(BlueprintException.class, () -> controller.get(ID));
    }
}
