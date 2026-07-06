package com.ispf.server.alert;

import com.ispf.server.persistence.AlarmShelfRepository;
import com.ispf.server.persistence.entity.AlarmShelfEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlarmShelfServiceTest {

    @Mock
    private AlarmShelfRepository repository;

    private AlarmShelfService service;

    @BeforeEach
    void setUp() {
        service = new AlarmShelfService(repository);
    }

    @Test
    void shelveCreatesActiveRecord() {
        when(repository.findActiveShelf(any(), any(), any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AlarmShelf shelf = service.shelve(new AlarmShelfService.ShelveAlarmRequest(
                "root.platform.devices.demo-sensor-01",
                "thresholdExceeded",
                null,
                15,
                "maintenance"
        ));

        assertEquals("root.platform.devices.demo-sensor-01", shelf.objectPath());
        assertEquals("thresholdExceeded", shelf.eventName());
        assertEquals("maintenance", shelf.comment());
        assertTrue(shelf.active());
        ArgumentCaptor<AlarmShelfEntity> captor = ArgumentCaptor.forClass(AlarmShelfEntity.class);
        verify(repository).save(captor.capture());
        long seconds = captor.getValue().getExpiresAt().getEpochSecond() - captor.getValue().getShelvedAt().getEpochSecond();
        assertTrue(seconds >= 14 * 60L && seconds <= 16 * 60L);
    }

    @Test
    void isShelvedReturnsTrueWhenActiveShelfExists() {
        AlarmShelfEntity entity = new AlarmShelfEntity();
        entity.setId("s1");
        entity.setObjectPath("root.device");
        entity.setEventName("alarm");
        entity.setShelvedBy("op");
        entity.setShelvedAt(Instant.now());
        entity.setActive(true);
        when(repository.findActiveShelf(eq("root.device"), eq("alarm"), any())).thenReturn(Optional.of(entity));

        assertTrue(service.isShelved("root.device", "alarm"));
        verify(repository, never()).deactivateExpired(any());
    }

    @Test
    void listActiveFiltersExpired() {
        AlarmShelfEntity active = new AlarmShelfEntity();
        active.setId("a1");
        active.setObjectPath("root.device");
        active.setEventName("alarm");
        active.setShelvedBy("op");
        active.setShelvedAt(Instant.now());
        active.setActive(true);

        AlarmShelfEntity expired = new AlarmShelfEntity();
        expired.setId("a2");
        expired.setObjectPath("root.device");
        expired.setEventName("alarm2");
        expired.setShelvedBy("op");
        expired.setShelvedAt(Instant.now().minusSeconds(120));
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        expired.setActive(true);

        when(repository.findByActiveTrueOrderByShelvedAtDesc()).thenReturn(List.of(active, expired));

        List<AlarmShelf> shelves = service.listActive();
        assertEquals(1, shelves.size());
        assertEquals("a1", shelves.get(0).id());
    }

    @Test
    void unshelveDeactivatesRecord() {
        AlarmShelfEntity entity = new AlarmShelfEntity();
        entity.setId("s1");
        entity.setActive(true);
        when(repository.findById("s1")).thenReturn(Optional.of(entity));

        service.unshelve("s1");

        assertFalse(entity.isActive());
        verify(repository).save(entity);
    }
}
