package com.ispf.server.plugin.blueprint;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.formula.AnalyticsFormulaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlueprintControllerMissingObjectAttachmentsTest {

    private static final String MISSING = "root.platform.devices.NO_SUCH";

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
    void missingObjectIsNotFoundBeforeAttachmentsAreListed() {
        when(objectManager.require(MISSING)).thenThrow(new ObjectNotFoundException(MISSING));

        assertThrows(ObjectNotFoundException.class, () -> controller.attachments(MISSING));

        verify(blueprintEngine, never()).attachmentsForObject(MISSING);
    }

    @Test
    void existingObjectStillListsItsAttachments() {
        when(blueprintEngine.attachmentsForObject("root.platform.devices.room")).thenReturn(List.of());

        controller.attachments("root.platform.devices.room");

        verify(objectManager).require("root.platform.devices.room");
        verify(blueprintEngine).attachmentsForObject("root.platform.devices.room");
    }
}
