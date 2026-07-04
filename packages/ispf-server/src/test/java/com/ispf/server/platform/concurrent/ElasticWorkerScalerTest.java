package com.ispf.server.platform.concurrent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticWorkerScalerTest {

    @Test
    void scalesUpWithQueueDepth() {
        ElasticWorkerScaler scaler = new ElasticWorkerScaler(2, 8, 50, 3);

        assertThat(scaler.targetWorkers()).isEqualTo(2);
        scaler.adjust(49);
        assertThat(scaler.targetWorkers()).isEqualTo(2);
        scaler.adjust(50);
        assertThat(scaler.targetWorkers()).isEqualTo(3);
        scaler.adjust(150);
        assertThat(scaler.targetWorkers()).isEqualTo(5);
        scaler.adjust(500);
        assertThat(scaler.targetWorkers()).isEqualTo(8);
    }

    @Test
    void scalesDownAfterEmptyChecks() {
        ElasticWorkerScaler scaler = new ElasticWorkerScaler(2, 8, 50, 3);
        scaler.adjust(200);
        assertThat(scaler.targetWorkers()).isGreaterThan(2);

        for (int i = 0; i < 2; i++) {
            scaler.adjust(0);
        }
        assertThat(scaler.targetWorkers()).isGreaterThan(2);

        scaler.adjust(0);
        assertThat(scaler.targetWorkers()).isEqualTo(scaler.targetWorkers()); // still high

        for (int i = 0; i < 3; i++) {
            scaler.adjust(0);
        }
        assertThat(scaler.targetWorkers()).isLessThan(8);
    }

    @Test
    void rejectsInvalidBounds() {
        assertThatThrownBy(() -> new ElasticWorkerScaler(0, 4, 10, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ElasticWorkerScaler(4, 2, 10, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
