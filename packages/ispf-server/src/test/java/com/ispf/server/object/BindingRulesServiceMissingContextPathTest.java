package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
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
class BindingRulesServiceMissingContextPathTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private PlatformObject dashboard;

    @Mock
    private BindingFormulaResolver bindingFormulaResolver;

    @Test
    void missingContextPathIsRejectedBeforeSave() {
        when(objectManager.require("root.platform.dashboards.ctx-proof")).thenReturn(dashboard);
        when(dashboard.type()).thenReturn(ObjectType.DASHBOARD);
        when(dashboard.getVariable(BindingRulesConstants.RULES_VARIABLE)).thenReturn(Optional.empty());
        when(objectManager.tree()).thenReturn(new ObjectTree());
        when(bindingFormulaResolver.resolve(any())).thenAnswer(invocation -> invocation.getArgument(0));
        BindingRulesService service = new BindingRulesService(
                objectManager, null, null, null, null, null, null, bindingFormulaResolver, null
        );
        BindingRule rule = new BindingRule(
                "ctx",
                "ctx",
                false,
                0,
                new BindingActivators(false, List.of(), null, 0),
                "",
                "false",
                new BindingTarget("context", null, "value", "root.platform.devices.ctx-missing", null)
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveRules("root.platform.dashboards.ctx-proof", List.of(rule))
        );

        assertEquals("Unknown object: root.platform.devices.ctx-missing", error.getMessage());
        verify(objectManager, never()).upsertSystemVariable(any(), any(), any(), any());
    }
}
