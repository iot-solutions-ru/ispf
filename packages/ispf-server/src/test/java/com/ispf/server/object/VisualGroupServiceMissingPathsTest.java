package com.ispf.server.object;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VisualGroupServiceMissingPathsTest {

    @Mock
    private ObjectManager objectManager;

    @Test
    void addWithoutPathsIsRejectedBeforeWalk() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new VisualGroupService(objectManager, null)
                        .addMembers("root.platform.devices.group-proof", null)
        );

        assertEquals("paths is required", exception.getMessage());
        verify(objectManager, never()).require(any());
        verify(objectManager, never()).tree();
    }
}
