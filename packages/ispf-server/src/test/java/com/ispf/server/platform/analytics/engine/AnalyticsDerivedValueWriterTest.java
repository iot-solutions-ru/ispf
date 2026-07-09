package com.ispf.server.platform.analytics.engine;

import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnalyticsDerivedValueWriterTest {

    @Mock
    private ObjectManager objectManager;

    @Test
    void writesWithObservedAtForClusterSync() {
        AnalyticsDerivedValueWriter writer = new AnalyticsDerivedValueWriter(objectManager);
        Instant observedAt = Instant.parse("2026-07-09T06:00:00Z");

        writer.write("root.platform.devices.tag-a", "derivedValue", "42", observedAt);

        ArgumentCaptor<Instant> observedCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(objectManager).setVariableValue(
                org.mockito.ArgumentMatchers.eq("root.platform.devices.tag-a"),
                org.mockito.ArgumentMatchers.eq("derivedValue"),
                org.mockito.ArgumentMatchers.any(),
                observedCaptor.capture()
        );
        assertThat(observedCaptor.getValue()).isEqualTo(observedAt);
    }
}
