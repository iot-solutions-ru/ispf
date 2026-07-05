package com.ispf.server.object.pubsub;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.ObjectChangeProperties;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectChangePublicationServiceTest {

    private static final String PATH = "root.dev.sensor";
    private static final String VAR = "temperature";
    private static final String EVENT = "alarmActive";

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private VariableChangeSubscriptionRegistry variableSubscriptionRegistry;

    @Mock
    private EventFiredSubscriptionRegistry eventFiredSubscriptionRegistry;

    @Mock
    private StructureChangeSubscriptionRegistry structureSubscriptionRegistry;

    @Mock
    private DeviceTelemetryPolicyService telemetryPolicyService;

    @Mock
    private ClusterProperties clusterProperties;

    private ObjectChangeProperties properties;
    private ObjectChangePublicationService service;

    @BeforeEach
    void setUp() {
        properties = new ObjectChangeProperties();
        properties.setDemandDrivenPublication(true);
        service = new ObjectChangePublicationService(
                eventPublisher,
                variableSubscriptionRegistry,
                eventFiredSubscriptionRegistry,
                structureSubscriptionRegistry,
                telemetryPolicyService,
                properties,
                clusterProperties
        );
    }

    @Test
    void skipsVariablePublicationWhenNoSubscribers() {
        when(variableSubscriptionRegistry.interest(PATH, VAR)).thenReturn(VariableChangeInterest.NONE);

        boolean published = service.publishVariableChange(PATH, VAR, null);

        assertThat(published).isFalse();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publishesHistorianOnlyForTelemetryOnlyDevice() {
        when(variableSubscriptionRegistry.interest(PATH, VAR))
                .thenReturn(new VariableChangeInterest(true, false, false, false, false, false));
        when(telemetryPolicyService.automationEligible(PATH)).thenReturn(false);

        boolean published = service.publishVariableChange(PATH, VAR, null);

        assertThat(published).isTrue();
        ArgumentCaptor<ObjectChangeEvent> captor = ArgumentCaptor.forClass(ObjectChangeEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ObjectChangeEvent event = captor.getValue();
        assertThat(event.telemetry()).isTrue();
        assertThat(event.automationEligible()).isFalse();
    }

    @Test
    void publishesUiRefreshWithoutAutomation() {
        when(variableSubscriptionRegistry.interest(PATH, VAR))
                .thenReturn(new VariableChangeInterest(false, false, false, false, false, true));
        when(telemetryPolicyService.automationEligible(PATH)).thenReturn(true);

        service.publishVariableChange(PATH, VAR, null);

        ArgumentCaptor<ObjectChangeEvent> captor = ArgumentCaptor.forClass(ObjectChangeEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().telemetry()).isFalse();
        assertThat(captor.getValue().automationEligible()).isFalse();
    }

    @Test
    void skipsEventFiredWhenNoSubscribers() {
        when(eventFiredSubscriptionRegistry.interest(PATH, EVENT)).thenReturn(EventFiredInterest.NONE);

        boolean published = service.publishEventFired(PATH, EVENT);

        assertThat(published).isFalse();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publishesEventFiredWhenBindingsSubscribe() {
        when(eventFiredSubscriptionRegistry.interest(PATH, EVENT))
                .thenReturn(new EventFiredInterest(true, false, false, false));

        service.publishEventFired(PATH, EVENT);

        ArgumentCaptor<ObjectChangeEvent> captor = ArgumentCaptor.forClass(ObjectChangeEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(ObjectChangeType.EVENT_FIRED);
        assertThat(captor.getValue().variableName()).isEqualTo(EVENT);
    }

    @Test
    void skipsStructureChangeWhenNoSubscribers() {
        ObjectChangeEvent template = ObjectChangeEvent.of(ObjectChangeType.UPDATED, PATH);
        when(clusterProperties.enabled()).thenReturn(false);
        when(structureSubscriptionRegistry.interest(ObjectChangeType.UPDATED, PATH))
                .thenReturn(StructureChangeInterest.NONE);

        boolean published = service.publishStructureChange(template);

        assertThat(published).isFalse();
    }

    @Test
    void publishesStructureChangeInClusterModeWithoutSubscribers() {
        ObjectChangeEvent template = ObjectChangeEvent.of(ObjectChangeType.DELETED, PATH);
        when(clusterProperties.enabled()).thenReturn(true);

        boolean published = service.publishStructureChange(template);

        assertThat(published).isTrue();
        verify(eventPublisher).publishEvent(template);
    }

    @Test
    void defersConfigVariableChangeUntilAfterCommit() {
        ObjectChangeEvent template = ObjectChangeEvent.variableUpdated(PATH, "diagram", 3L, "admin");
        when(variableSubscriptionRegistry.interest(PATH, "diagram")).thenReturn(VariableChangeInterest.NONE);
        when(telemetryPolicyService.automationEligible(PATH)).thenReturn(false);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.publishConfigVariableChangeAfterCommit(template);
            verify(eventPublisher, never()).publishEvent(any());

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(eventPublisher).publishEvent(any(ObjectChangeEvent.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void alwaysPublishesConfigVariableChangeEvenWithoutSubscribers() {
        ObjectChangeEvent template = ObjectChangeEvent.variableUpdated(PATH, "diagram", 3L, "admin");
        when(variableSubscriptionRegistry.interest(PATH, "diagram")).thenReturn(VariableChangeInterest.NONE);
        when(telemetryPolicyService.automationEligible(PATH)).thenReturn(false);

        boolean published = service.publishConfigVariableChange(template);

        assertThat(published).isTrue();
        verify(eventPublisher).publishEvent(any(ObjectChangeEvent.class));
    }

    @Test
    void defersStructureChangeUntilAfterCommit() {
        ObjectChangeEvent template = ObjectChangeEvent.of(ObjectChangeType.CREATED, PATH);
        when(structureSubscriptionRegistry.interest(ObjectChangeType.CREATED, PATH))
                .thenReturn(new StructureChangeInterest(false, true, false, true));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.publishStructureChangeAfterCommit(template);
            verify(eventPublisher, never()).publishEvent(any());

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(eventPublisher).publishEvent(template);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void legacyModeAlwaysPublishesVariableChange() {
        properties.setDemandDrivenPublication(false);
        when(telemetryPolicyService.automationEligible(PATH)).thenReturn(true);

        service.publishVariableChange(PATH, VAR, null);

        verify(eventPublisher).publishEvent(any(ObjectChangeEvent.class));
        verify(variableSubscriptionRegistry, never()).interest(eq(PATH), eq(VAR));
    }
}
