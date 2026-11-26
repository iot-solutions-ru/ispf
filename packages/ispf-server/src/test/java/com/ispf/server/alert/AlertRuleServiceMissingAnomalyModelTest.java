package com.ispf.server.alert;

import com.ispf.core.ml.AnomalyDetectionSpi;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.alert.AlertRuleService.CreateAlertRuleRequest;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.config.MlAnomalyProperties;
import com.ispf.server.config.NotificationProperties;
import com.ispf.server.ml.AnomalyAlertRuleEvaluator;
import com.ispf.server.notification.NotificationDispatchService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceMissingAnomalyModelTest {

    @Mock
    private AutomationTreeService automationTreeService;

    @Mock
    private ObjectManager objectManager;

    @Test
    void unknownAnomalyModelIsRejectedBeforeSave() {
        PlatformObject node = mock(PlatformObject.class);
        when(objectManager.require("root.platform.devices.anomaly-proof")).thenReturn(node);
        when(node.getVariable("temp")).thenReturn(Optional.of(mock(Variable.class)));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service("noop").create(request("NO_SUCH"))
        );

        assertEquals("Unknown anomalyModelId: NO_SUCH", exception.getMessage());
        verifyNoInteractions(automationTreeService);
    }

    @Test
    void loadedAnomalyModelIsAccepted() {
        assertDoesNotThrow(() -> evaluator("noop").requireKnownModel("noop"));
    }

    private AlertRuleService service(String modelId) {
        return new AlertRuleService(
                automationTreeService,
                objectManager,
                null,
                null,
                null,
                null,
                new NotificationDispatchService(new NotificationProperties(), null),
                null,
                evaluator(modelId)
        );
    }

    private static AnomalyAlertRuleEvaluator evaluator(String modelId) {
        AnomalyDetectionSpi spi = mock(AnomalyDetectionSpi.class);
        when(spi.modelId()).thenReturn(modelId);
        return new AnomalyAlertRuleEvaluator(spi, new MlAnomalyProperties(), null, null);
    }

    private static CreateAlertRuleRequest request(String anomalyModelId) {
        return new CreateAlertRuleRequest(
                "anomaly-proof",
                "root.platform.devices.anomaly-proof",
                "temp",
                "self.temp[\"value\"] > 1000",
                "anomaly-proof",
                null,
                false,
                false,
                0,
                false,
                "HIGH",
                false,
                null,
                null,
                anomalyModelId,
                null,
                0,
                0,
                null,
                null
        );
    }
}
