package com.ispf.server.correlator;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.correlator.EventCorrelatorService.CreateCorrelatorRequest;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventCorrelatorServiceSetVariableTargetTest {

    private static final String DEVICE = "root.platform.devices.setvar-proof";

    @Mock
    private AutomationTreeService automationTreeService;

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ObjectTree objectTree;

    @Mock
    private PlatformObject device;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final Variable temp = new Variable(
            "temp",
            DataSchema.builder("temp").field("value", FieldType.DOUBLE).build(),
            true,
            true,
            null
    );

    @Test
    void missingVariableIsRejectedBeforeSave() {
        stubDevice("ghost");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(request("ghost=1"))
        );

        assertEquals("Unknown variable: ghost", error.getMessage());
        verifyNeverCreated();
    }

    @Test
    void assignmentWithoutNameIsRejectedBeforeSave() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(request("=1"))
        );

        assertEquals("SET_VARIABLE actionTarget must be variableName=value", error.getMessage());
        verifyNeverCreated();
    }

    @Test
    void valueOutsideSchemaIsRejectedBeforeSave() {
        stubDevice("temp");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(request("temp=abc"))
        );

        assertEquals("value schema is not compatible with variable temp", error.getMessage());
        verifyNeverCreated();
    }

    private void stubDevice(String missingOrTemp) {
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(DEVICE)).thenReturn(Optional.of(device));
        if ("temp".equals(missingOrTemp)) {
            when(device.getVariable("temp")).thenReturn(Optional.of(temp));
        } else {
            when(device.getVariable("ghost")).thenReturn(Optional.empty());
        }
    }

    private void verifyNeverCreated() {
        verify(automationTreeService, never()).createCorrelator(
                any(), any(), any(), any(), any(),
                anyInt(), anyInt(), anyInt(), anyInt(),
                any(), any(), any(), anyBoolean()
        );
    }

    private EventCorrelatorService service() {
        return new EventCorrelatorService(
                automationTreeService,
                null,
                null,
                null,
                null,
                objectManager,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                transactionManager
        );
    }

    private static CreateCorrelatorRequest request(String actionTarget) {
        return new CreateCorrelatorRequest(
                "setvar-proof",
                DEVICE,
                CorrelatorPatternType.COUNT,
                "setvar-probe",
                null,
                0,
                1,
                0,
                0,
                CorrelatorActionType.SET_VARIABLE,
                actionTarget,
                "",
                false
        );
    }
}
