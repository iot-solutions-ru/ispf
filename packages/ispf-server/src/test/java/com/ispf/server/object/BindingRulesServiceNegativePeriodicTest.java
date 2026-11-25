package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BindingRulesServiceNegativePeriodicTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private PlatformObject device;

    @Test
    void negativePeriodicIsRejectedBeforeSave() {
        when(objectManager.require("root.platform.devices.period-proof")).thenReturn(device);
        when(device.getVariable(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.<Variable>empty());
        BindingRulesService service = new BindingRulesService(
                objectManager, null, null, null, null, null, null, null, null
        );
        BindingRule rule = new BindingRule(
                "period-proof",
                "period-proof",
                false,
                0,
                new BindingActivators(false, List.of(), null, -8L),
                "",
                "1",
                new BindingTarget("out", "value")
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveRules("root.platform.devices.period-proof", List.of(rule))
        );

        assertEquals("periodicMs must not be negative", error.getMessage());
        verify(objectManager, never()).upsertSystemVariable(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
