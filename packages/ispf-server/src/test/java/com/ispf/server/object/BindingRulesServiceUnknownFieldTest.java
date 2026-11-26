package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.platform.analytics.formula.BindingFormulaResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BindingRulesServiceUnknownFieldTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private PlatformObject device;

    @Mock
    private Variable variable;

    @Mock
    private BindingFormulaResolver bindingFormulaResolver;

    @Test
    void unknownFieldIsRejectedBeforeSave() {
        when(objectManager.require("root.platform.devices.field-proof")).thenReturn(device);
        when(device.getVariable("temp")).thenReturn(Optional.of(variable));
        when(device.getVariable(BindingRulesConstants.RULES_VARIABLE)).thenReturn(Optional.empty());
        when(variable.schema()).thenReturn(DataSchema.builder("temp").field("value", FieldType.DOUBLE).build());
        when(bindingFormulaResolver.resolve(any())).thenAnswer(invocation -> invocation.getArgument(0));
        BindingRulesService service = new BindingRulesService(
                objectManager, null, null, null, null, null, null, bindingFormulaResolver, null
        );
        BindingRule rule = new BindingRule(
                "field-proof",
                "field-proof",
                false,
                0,
                new BindingActivators(false, List.of(), null, 0),
                "",
                "1",
                new BindingTarget("temp", "nope")
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveRules("root.platform.devices.field-proof", List.of(rule))
        );

        assertEquals("Unknown target field: nope", error.getMessage());
        verify(objectManager, never()).upsertSystemVariable(any(), any(), any(), any());
    }
}
