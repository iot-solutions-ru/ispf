package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.object.pubsub.VariableChangeInterest;
import com.ispf.server.object.pubsub.VariableChangeSubscriptionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClusterVariableReplicaApplierTest {

    private static final String PATH = "root.platform.devices.test-device";
    private static final String VAR = "temperature";

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private VariableChangeSubscriptionRegistry variableSubscriptionRegistry;

    @Mock
    private VariableChangeInterest interest;

    private ClusterVariableReplicaApplier applier;

    @BeforeEach
    void setUp() {
        applier = new ClusterVariableReplicaApplier(objectManager, eventPublisher, variableSubscriptionRegistry);
        when(variableSubscriptionRegistry.interest(PATH, VAR)).thenReturn(interest);
    }

    @Test
    void appliesValueAndPublishesReplicaIngressEventWhenLiveObserverExists() {
        when(interest.liveObserver()).thenReturn(true);
        DataRecord value = DataRecord.single(
                DataSchema.builder(VAR).field("value", FieldType.DOUBLE).build(),
                Map.of("value", 42.0)
        );

        applier.apply(PATH, VAR, value, null);

        verify(objectManager).setDriverTelemetryValueDirect(eq(PATH), eq(VAR), eq(value));
        ArgumentCaptor<ObjectChangeEvent> captor = ArgumentCaptor.forClass(ObjectChangeEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ObjectChangeEvent event = captor.getValue();
        assertThat(event.replicaIngress()).isTrue();
        assertThat(event.path()).isEqualTo(PATH);
        assertThat(event.variableName()).isEqualTo(VAR);
        assertThat(event.automationEligible()).isFalse();
    }

    @Test
    void skipsApplyWhenNoLiveObserver() {
        when(interest.liveObserver()).thenReturn(false);
        DataRecord value = DataRecord.single(
                DataSchema.builder(VAR).field("value", FieldType.DOUBLE).build(),
                Map.of("value", 42.0)
        );

        applier.apply(PATH, VAR, value, null);

        verify(objectManager, never()).setDriverTelemetryValueDirect(eq(PATH), eq(VAR), eq(value));
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }
}
