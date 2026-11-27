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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void invalidCronAndTimeZoneDoNotCreateTheSchedule() {
        when(objectManager.tree()).thenReturn(new ObjectTree());

        IllegalArgumentException cron = assertThrows(IllegalArgumentException.class, () ->
                service.create("bad-cron", null, null, false, 60_000L, "not a cron", null, null, null));
        IllegalArgumentException zone = assertThrows(IllegalArgumentException.class, () ->
                service.create("bad-zone", null, null, false, 60_000L, null, "Not/AZone", null, null));

        assertTrue(cron.getMessage().startsWith("Invalid schedule cronExpression"));
        assertEquals("Invalid schedule timeZone: Not/AZone", zone.getMessage());
        verify(objectManager, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void nonPositiveIntervalDoesNotCreateTheSchedule() {
        when(objectManager.tree()).thenReturn(new ObjectTree());

        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class, () ->
                service.create("neg", null, null, false, -1L, null, null, null, null));
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class, () ->
                service.create("zero", null, null, false, 0L, null, null, null, null));

        assertEquals("intervalMs must be positive", negative.getMessage());
        assertEquals("intervalMs must be positive", zero.getMessage());
        verify(objectManager, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void oversizedIntervalIsRejectedBeforeSave() {
        when(objectManager.tree()).thenReturn(new ObjectTree());

        IllegalArgumentException created = assertThrows(IllegalArgumentException.class, () ->
                service.create("sched-proof", null, null, false, 999_999_999_999L, null, null, null, null));
        IllegalArgumentException updated = assertThrows(IllegalArgumentException.class, () ->
                service.update("root.platform.schedules.sched-proof", null, null, null, 999_999_999_999L, null, null, null, null));

        assertEquals("intervalMs must not exceed 2147483647", created.getMessage());
        assertEquals("intervalMs must not exceed 2147483647", updated.getMessage());
        verify(objectManager, never()).create(any(), any(), any(), any(), any(), any());
        verify(objectManager, never()).require(any());
    }
}
