package com.ispf.server.object;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.ObjectTree;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.security.PlatformRoleService;
import com.ispf.server.security.PlatformUserService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObjectBulkDeleteServiceMissingPathTest {

    @Test
    void missingPathFailsThatItemAndKeepsTheDeletedOne() {
        ObjectManager objectManager = mock(ObjectManager.class);
        ObjectTree tree = mock(ObjectTree.class);
        when(objectManager.tree()).thenReturn(tree);
        when(tree.findByPath(anyString())).thenReturn(Optional.empty());
        TreeCrudService treeCrudService = mock(TreeCrudService.class);
        doThrow(new ObjectNotFoundException("root.platform.devices.missing"))
                .when(treeCrudService).delete("root.platform.devices.missing");
        ObjectBulkDeleteService service = new ObjectBulkDeleteService(
                objectManager,
                mock(PlatformUserService.class),
                mock(PlatformRoleService.class),
                mock(AutomationTreeService.class),
                treeCrudService
        );

        ObjectBulkDeleteService.BulkDeleteResult result = service.deleteAll(java.util.List.of(
                "root.platform.devices.extra",
                "root.platform.devices.missing"
        ));

        assertEquals(1, result.deleted());
        assertTrue(result.results().get(0).success());
        assertEquals("root.platform.devices.extra", result.results().get(0).path());
        assertFalse(result.results().get(1).success());
        assertEquals("root.platform.devices.missing", result.results().get(1).path());
        assertEquals("Object not found: root.platform.devices.missing", result.results().get(1).error());
        verify(objectManager, never()).delete(anyString());
    }
}
