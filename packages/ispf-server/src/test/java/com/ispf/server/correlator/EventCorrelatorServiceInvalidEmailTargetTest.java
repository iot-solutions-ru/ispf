package com.ispf.server.correlator;

import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.config.NotificationProperties;
import com.ispf.server.correlator.EventCorrelatorService.CreateCorrelatorRequest;
import com.ispf.server.notification.NotificationDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventCorrelatorServiceInvalidEmailTargetTest {

    @Mock
    private AutomationTreeService automationTreeService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    void invalidEmailTargetIsRejectedBeforeSave() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(request("not-an-email"))
        );

        assertEquals("Email target format: to@host|subject|body", exception.getMessage());
        verify(automationTreeService, never()).createCorrelator(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                any(),
                any(),
                any(),
                anyBoolean()
        );
    }

    private EventCorrelatorService service() {
        return new EventCorrelatorService(
                automationTreeService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new NotificationDispatchService(new NotificationProperties(), null),
                null,
                null,
                transactionManager
        );
    }

    private static CreateCorrelatorRequest request(String actionTarget) {
        return new CreateCorrelatorRequest(
                "email-proof",
                "",
                CorrelatorPatternType.COUNT,
                "email-probe",
                null,
                0,
                1,
                0,
                0,
                CorrelatorActionType.SEND_EMAIL,
                actionTarget,
                "",
                false
        );
    }
}
