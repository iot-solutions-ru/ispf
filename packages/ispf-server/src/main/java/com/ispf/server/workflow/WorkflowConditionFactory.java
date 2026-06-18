package com.ispf.server.workflow;

import com.ispf.core.object.PlatformObject;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.ExpressionException;
import com.ispf.plugin.workflow.WorkflowConditionEvaluator;
import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;

@Component
public class WorkflowConditionFactory {

    private final ObjectManager objectManager;
    private final ExpressionEngine expressionEngine;

    public WorkflowConditionFactory(ObjectManager objectManager, ExpressionEngine expressionEngine) {
        this.objectManager = objectManager;
        this.expressionEngine = expressionEngine;
    }

    public WorkflowConditionEvaluator forTriggerObjectPath(String triggerObjectPath) {
        return expression -> evaluateExpression(expression, triggerObjectPath);
    }

    private boolean evaluateExpression(String expression, String triggerObjectPath) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        if (triggerObjectPath == null || triggerObjectPath.isBlank()) {
            return false;
        }
        try {
            PlatformObject node = objectManager.require(triggerObjectPath);
            Object result = expressionEngine.evaluate(expression, node);
            if (result instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(String.valueOf(result));
        } catch (ExpressionException | IllegalArgumentException e) {
            return false;
        }
    }
}
