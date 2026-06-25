package com.ispf.server.object.bus;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectChangeWorkerScalerTest {

    @Test
    void scalesUpWhenQueueExceedsThreshold() {
        ObjectChangeWorkerScaler scaler = new ObjectChangeWorkerScaler(2, 8, 50, 3);
        assertThat(scaler.targetWorkers()).isEqualTo(2);

        scaler.adjust(49);
        assertThat(scaler.targetWorkers()).isEqualTo(2);

        scaler.adjust(50);
        assertThat(scaler.targetWorkers()).isEqualTo(3);

        scaler.adjust(150);
        assertThat(scaler.targetWorkers()).isEqualTo(5);

        scaler.adjust(10_000);
        assertThat(scaler.targetWorkers()).isEqualTo(8);
    }

    @Test
    void scalesDownAfterSustainedEmptyQueue() {
        ObjectChangeWorkerScaler scaler = new ObjectChangeWorkerScaler(2, 8, 50, 3);
        scaler.adjust(200);
        assertThat(scaler.targetWorkers()).isEqualTo(6);

        scaler.adjust(0);
        scaler.adjust(0);
        assertThat(scaler.targetWorkers()).isEqualTo(6);

        scaler.adjust(0);
        assertThat(scaler.targetWorkers()).isEqualTo(5);
    }

    @Test
    void emptyCheckResetsWhenQueueHasItems() {
        ObjectChangeWorkerScaler scaler = new ObjectChangeWorkerScaler(2, 8, 50, 2);
        scaler.adjust(100);
        int peak = scaler.targetWorkers();

        scaler.adjust(0);
        scaler.adjust(5);
        scaler.adjust(0);
        assertThat(scaler.targetWorkers()).isEqualTo(peak);
    }

    @Test
    void rejectsInvalidBounds() {
        assertThatThrownBy(() -> new ObjectChangeWorkerScaler(0, 4, 10, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ObjectChangeWorkerScaler(4, 2, 10, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
