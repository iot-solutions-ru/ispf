package com.ispf.server.api;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.security.acl.ObjectAclStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectAclControllerMissingObjectTest {

    private static final String MISSING = "root.platform.devices.NO_SUCH";
    private static final String EXISTING = "root.platform.devices.acl-proof";

    @Mock
    private ObjectAccessService objectAccessService;

    @Mock
    private ObjectManager objectManager;

    @Test
    void missingPathListDoesNotReadEntries() {
        when(objectManager.require(MISSING)).thenThrow(new ObjectNotFoundException(MISSING));

        ObjectNotFoundException exception = assertThrows(
                ObjectNotFoundException.class,
                () -> controller().list(MISSING)
        );

        assertEquals("Object not found: " + MISSING, exception.getMessage());
        verify(objectAccessService, never()).listEntries(any());
    }

    @Test
    void missingPathReplaceDoesNotStore() {
        when(objectManager.require(MISSING)).thenThrow(new ObjectNotFoundException(MISSING));
        ObjectAclController.ReplaceAclRequest request = new ObjectAclController.ReplaceAclRequest(List.of(
                new ObjectAclController.AclEntryDto("ROLE", "operator", "READ")
        ));

        assertThrows(ObjectNotFoundException.class, () -> controller().replace(MISSING, request));
        verify(objectAccessService, never()).replaceEntries(any(), anyList());
    }

    @Test
    void existingObjectWithoutEntriesReturnsEmpty() {
        when(objectManager.require(EXISTING)).thenReturn(null);
        when(objectAccessService.listEntries(EXISTING)).thenReturn(List.of());

        Map<String, Object> body = controller().list(EXISTING);

        assertEquals(EXISTING, body.get("objectPath"));
        assertEquals(List.of(), body.get("entries"));
    }

    @Test
    void existingObjectReplaceStoresEntries() {
        when(objectManager.require(EXISTING)).thenReturn(null);
        ObjectAclController.ReplaceAclRequest request = new ObjectAclController.ReplaceAclRequest(List.of(
                new ObjectAclController.AclEntryDto("ROLE", "operator", "READ")
        ));

        Map<String, Object> body = controller().replace(EXISTING, request);

        assertEquals("updated", body.get("status"));
        assertEquals(1, body.get("count"));
        verify(objectAccessService).replaceEntries(
                EXISTING,
                List.of(new ObjectAclStore.ObjectAclEntryDraft("ROLE", "operator", "READ"))
        );
    }

    private ObjectAclController controller() {
        return new ObjectAclController(objectAccessService, objectManager);
    }
}
