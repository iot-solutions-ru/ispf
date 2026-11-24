package com.ispf.server.alert;

import com.ispf.server.alert.AlertRuleService.CreateAlertRuleRequest;
import com.ispf.server.alert.AlertRuleService.UpdateAlertRuleRequest;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.config.NotificationProperties;
import com.ispf.server.notification.NotificationDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceInvalidEmailTargetTest {

    @Mock
    private AutomationTreeService automationTreeService;

    @Test
    void invalidEmailTargetIsRejectedBeforeSave() {
        AlertRuleService service = service();

        IllegalArgumentException created = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(createRequest("not-an-email"))
        );
        IllegalArgumentException updated = assertThrows(
                IllegalArgumentException.class,
                () -> service.update("root.platform.alert-rules.email-proof", updateRequest("not-an-email"))
        );

        assertEquals("Email target format: to@host|subject|body", created.getMessage());
        assertEquals("Email target format: to@host|subject|body", updated.getMessage());
        verifyNoInteractions(automationTreeService);
    }

    private AlertRuleService service() {
        return new AlertRuleService(
                automationTreeService,
                null,
                null,
                null,
                null,
                null,
                new NotificationDispatchService(new NotificationProperties(), new ObjectMapper()),
                null,
                null
        );
    }

    private static CreateAlertRuleRequest createRequest(String notificationEmailTarget) {
        return new CreateAlertRuleRequest(
                "email-proof",
                "root.platform.devices.email-proof",
                "value",
                "true",
                "email-probe",
                "",
                false,
                false,
                0,
                false,
                "HIGH",
                false,
                null,
                notificationEmailTarget,
                null,
                null,
                0,
                0,
                null,
                null
        );
    }

    private static UpdateAlertRuleRequest updateRequest(String notificationEmailTarget) {
        return new UpdateAlertRuleRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                notificationEmailTarget,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
