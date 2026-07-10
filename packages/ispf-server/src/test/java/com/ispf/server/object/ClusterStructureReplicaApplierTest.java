package com.ispf.server.object;

import com.ispf.server.object.pubsub.StructureChangeInterest;
import com.ispf.server.object.pubsub.StructureChangeSubscriptionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClusterStructureReplicaApplierTest {

    private static final String PATH = "root.platform.devices.test-device";

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private StructureChangeSubscriptionRegistry structureSubscriptionRegistry;

    @Mock
    private StructureChangeInterest interest;

    private ClusterStructureReplicaApplier applier;

    @BeforeEach
    void setUp() {
        applier = new ClusterStructureReplicaApplier(objectManager, eventPublisher, structureSubscriptionRegistry);
    }

    @Test
    void reloadsFromDatabaseAndPublishesReplicaIngressWhenLiveObserverExists() {
        when(structureSubscriptionRegistry.interest(ObjectChangeType.CREATED, PATH)).thenReturn(interest);
        when(interest.liveObserver()).thenReturn(true);

        applier.apply(ObjectChangeType.CREATED, PATH, null);

        verify(objectManager).reloadPathFromDatabase(PATH);
        ArgumentCaptor<ObjectChangeEvent> captor = ArgumentCaptor.forClass(ObjectChangeEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ObjectChangeEvent event = captor.getValue();
        assertThat(event.replicaIngress()).isTrue();
        assertThat(event.type()).isEqualTo(ObjectChangeType.CREATED);
        assertThat(event.path()).isEqualTo(PATH);
        assertThat(event.automationEligible()).isFalse();
    }

    @Test
    void reloadsFromDatabaseWithoutWsWhenNoLiveObserver() {
        when(structureSubscriptionRegistry.interest(ObjectChangeType.UPDATED, PATH)).thenReturn(interest);
        when(interest.liveObserver()).thenReturn(false);

        applier.apply(ObjectChangeType.UPDATED, PATH, null);

        verify(objectManager).reloadPathFromDatabase(PATH);
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void removesDeletedPathFromMemory() {
        when(structureSubscriptionRegistry.interest(ObjectChangeType.DELETED, PATH)).thenReturn(interest);
        when(interest.liveObserver()).thenReturn(false);

        applier.apply(ObjectChangeType.DELETED, PATH, null);

        verify(objectManager).removePathFromMemoryIfPresent(PATH);
        verify(objectManager, never()).reloadPathFromDatabase(eq(PATH));
    }
}
