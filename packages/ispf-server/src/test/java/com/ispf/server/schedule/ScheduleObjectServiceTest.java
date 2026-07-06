package com.ispf.server.schedule;

import com.ispf.core.object.ObjectTree;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleObjectServiceTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private SystemObjectStructureService structureService;

    private ScheduleObjectService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleObjectService(objectManager, structureService, new ObjectMapper());
    }

    @Test
    void listEnabledWhenCatalogMissingReturnsEmptyWithoutWrites() {
        ObjectTree tree = new ObjectTree();
        when(objectManager.tree()).thenReturn(tree);

        List<ScheduleObjectService.ScheduleDefinition> schedules = service.listEnabled();

        assertTrue(schedules.isEmpty());
        verify(objectManager, never()).ensureSystemCatalogFolder(any(), any(), any());
        verify(objectManager, never()).create(any(), any(), any(), any(), any(), any());
    }
}
