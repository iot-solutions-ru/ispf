package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.expression.BindingEvaluationContext;
import com.ispf.expression.BindingExpressionEvaluator;
import com.ispf.expression.ExpressionEngine;
import com.ispf.server.binding.BindingInvokeAuditService;
import com.ispf.server.event.EventService;
import com.ispf.server.object.pubsub.ObjectChangePublicationService;
import com.ispf.server.persistence.ObjectEntityMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BindingRuleEngineUnknownVariableTargetTest {

    private static final String PATH = "root.platform.devices.variable-target-probe";

    @Test
    void unknownVariableNameIsAuditedWithoutAbortingPass() {
        BindingInvokeAuditService audit = mock(BindingInvokeAuditService.class);
        BindingRuleEngine engine = engineWith(new BindingTarget("missingVar", "value"), audit);

        engine.runRulesForObject(PATH);

        verify(audit).recordCel(
                eq(PATH),
                any(BindingRule.class),
                anyString(),
                eq(false),
                eq(false),
                contains("Unknown target variable: missingVar"),
                anyLong(),
                any()
        );
    }

    @Test
    void unknownVariableRefIsAuditedWithoutAbortingPass() {
        BindingInvokeAuditService audit = mock(BindingInvokeAuditService.class);
        BindingRuleEngine engine = engineWith(
                new BindingTarget("variable", null, "value", null, null, "@/missingVar"),
                audit
        );

        engine.runRulesForObject(PATH);

        verify(audit).recordCel(
                eq(PATH),
                any(BindingRule.class),
                anyString(),
                eq(false),
                eq(false),
                contains("Unknown target variable: @/missingVar"),
                anyLong(),
                any()
        );
    }

    private static BindingRuleEngine engineWith(BindingTarget target, BindingInvokeAuditService audit) {
        ObjectTree tree = new ObjectTree();
        PlatformObject node = new PlatformObject(
                "variable-target-probe",
                PATH,
                ObjectType.DEVICE,
                "probe",
                "",
                null
        );
        tree.register(node);
        ObjectManager objectManager = mock(ObjectManager.class);
        when(objectManager.tree()).thenReturn(tree);
        when(objectManager.require(PATH)).thenReturn(node);
        BindingRulesService bindingRulesService = mock(BindingRulesService.class);
        when(bindingRulesService.listRules(PATH)).thenReturn(List.of(new BindingRule(
                "rule-var",
                "var",
                true,
                0,
                new BindingActivators(false, List.of(), null, 0),
                "",
                "1",
                target
        )));
        return new BindingRuleEngine(
                objectManager,
                bindingRulesService,
                mock(BindingExpressionEvaluator.class),
                mock(ExpressionEngine.class),
                mock(BindingEvaluationContext.class),
                mock(ObjectChangePublicationService.class),
                mock(BindingRuleAsyncExecutor.class),
                audit,
                mock(ObjectEntityMapper.class),
                new ObjectMapper(),
                mock(EventService.class)
        );
    }
}
