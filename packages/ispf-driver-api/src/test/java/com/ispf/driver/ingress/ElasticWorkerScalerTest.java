package com.ispf.driver.ingress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticWorkerScalerTest {

    @Test
    void scalesUpWithQueueDepth() {
        ElasticWorkerScaler scaler = new ElasticWorkerScaler(2, 8, 50, 3);

        assertEquals(2, scaler.targetWorkers());
        scaler.adjust(49);
        assertEquals(2, scaler.targetWorkers());
        scaler.adjust(50);
        assertEquals(3, scaler.targetWorkers());
        scaler.adjust(150);
        assertEquals(5, scaler.targetWorkers());
        scaler.adjust(500);
        assertEquals(8, scaler.targetWorkers());
    }

    @Test
    void scalesDownAfterEmptyChecks() {
        ElasticWorkerScaler scaler = new ElasticWorkerScaler(2, 8, 50, 3);
        scaler.adjust(200);
        assertTrue(scaler.targetWorkers() > 2);

        for (int i = 0; i < 2; i++) {
            scaler.adjust(0);
        }
        assertTrue(scaler.targetWorkers() > 2);

        for (int i = 0; i < 3; i++) {
            scaler.adjust(0);
        }
        assertTrue(scaler.targetWorkers() < 8);
    }

    @Test
    void rejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> new ElasticWorkerScaler(0, 4, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> new ElasticWorkerScaler(4, 2, 10, 1));
    }
}
