package com.ispf.server.binding;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlBindingObjectServiceMissingTargetPathTest {

    @Test
    void missingTargetIsRejectedBeforeTheBindingFieldsAreWritten() {
        ObjectManager objectManager = mock(ObjectManager.class);
        PlatformObject binding = mock(PlatformObject.class);
        when(binding.type()).thenReturn(ObjectType.BINDING);
        when(objectManager.require("root.platform.bindings.target-proof")).thenReturn(binding);
        when(objectManager.require("root.platform.devices.missing"))
                .thenThrow(new ObjectNotFoundException("root.platform.devices.missing"));
        SqlBindingObjectService service = new SqlBindingObjectService(
                objectManager, null, null, null, null);

        assertThrows(ObjectNotFoundException.class, () -> service.update(
                "root.platform.bindings.target-proof",
                new SqlBindingObjectService.BindingDefinition(
                        "root.platform.bindings.target-proof",
                        "target-proof",
                        "root.platform.devices.missing",
                        "value",
                        "",
                        "SELECT 1 AS value",
                        "value",
                        "manual",
                        30_000L,
                        "",
                        "",
                        false,
                        null
                )
        ));

        verify(objectManager, never()).setVariableValue(any(), any(), any());
        verify(objectManager, never()).tree();
    }
}
