package com.ispf.server.alert;

import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.AlarmShelfRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlarmShelfServiceMissingTargetTest {

    private static final String DEVICE = "root.platform.devices.shelf-proof";
    private static final String MISSING_DEVICE = "root.platform.devices.shelf-proof-missing";
    private static final String RULE = "root.platform.alert-rules.shelf-proof";
    private static final String MISSING_RULE = "root.platform.alert-rules.shelf-proof-missing";

    @Mock
    private AlarmShelfRepository repository;

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ObjectTree objectTree;

    @Mock
    private PlatformObject device;

    private AlarmShelfService service;

    @BeforeEach
    void setUp() {
        service = new AlarmShelfService(repository, objectManager);
    }

    @Test
    void missingObjectIsRejectedBeforeSave() {
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(MISSING_DEVICE)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.shelve(request(MISSING_DEVICE, "probe", null))
        );

        assertEquals("Unknown object: " + MISSING_DEVICE, error.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void missingEventIsRejectedBeforeSave() {
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(DEVICE)).thenReturn(Optional.of(device));
        when(device.events()).thenReturn(Map.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.shelve(request(DEVICE, "noSuchEvent", null))
        );

        assertEquals("Unknown event: noSuchEvent", error.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void blankEventIsRejectedBeforeSave() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.shelve(request(DEVICE, " ", null))
        );

        assertEquals("eventName is required", error.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void missingAlertRuleIsRejectedBeforeSave() {
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(DEVICE)).thenReturn(Optional.of(device));
        when(device.events()).thenReturn(Map.of("probe", mock(EventDescriptor.class)));
        when(objectTree.findByPath(MISSING_RULE)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.shelve(request(DEVICE, "probe", MISSING_RULE))
        );

        assertEquals("Unknown alert rule: " + MISSING_RULE, error.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void existingTargetIsSaved() {
        PlatformObject rule = mock(PlatformObject.class);
        when(rule.type()).thenReturn(ObjectType.ALERT);
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(DEVICE)).thenReturn(Optional.of(device));
        when(device.events()).thenReturn(Map.of("probe", mock(EventDescriptor.class)));
        when(objectTree.findByPath(RULE)).thenReturn(Optional.of(rule));
        when(repository.findActiveShelf(any(), any(), any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AlarmShelf shelf = service.shelve(request(DEVICE, "probe", RULE));

        assertEquals(DEVICE, shelf.objectPath());
        assertEquals("probe", shelf.eventName());
        assertEquals(RULE, shelf.alertRulePath());
        verify(repository).save(any());
    }

    private static AlarmShelfService.ShelveAlarmRequest request(
            String objectPath,
            String eventName,
            String alertRulePath
    ) {
        return new AlarmShelfService.ShelveAlarmRequest(objectPath, eventName, alertRulePath, 5, null);
    }
}
