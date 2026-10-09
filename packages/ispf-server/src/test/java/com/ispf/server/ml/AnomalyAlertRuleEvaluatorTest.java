package com.ispf.server.ml;

import com.ispf.core.ml.AnomalyDetectionSpi;
import com.ispf.server.config.MlAnomalyProperties;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnomalyAlertRuleEvaluatorTest {

    @Mock
    AnomalyDetectionSpi anomalyDetectionSpi;
    @Mock
    VariableHistoryService variableHistoryService;
    @Mock
    ObjectManager objectManager;

    private AnomalyAlertRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        MlAnomalyProperties properties = new MlAnomalyProperties();
        properties.setEnabled(true);
        evaluator = new AnomalyAlertRuleEvaluator(
                anomalyDetectionSpi,
                properties,
                variableHistoryService,
                objectManager
        );
    }

    @Test
    void historyReadFailureIsNotAMissingAnomaly() {
        when(anomalyDetectionSpi.modelId()).thenReturn("threshold-v1");
        when(variableHistoryService.query(anyString(), anyString(), anyString(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("history store down"));

        assertThatThrownBy(() -> evaluator.evaluate(
                "root.platform.devices.pump",
                "temperature",
                "threshold-v1"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Anomaly history read failed")
                .hasMessageContaining("history store down")
                .hasMessageContaining("root.platform.devices.pump/temperature");

        verify(anomalyDetectionSpi, never()).score(anyString(), anyString(), any());
        verify(objectManager, never()).require(anyString());
    }
}
