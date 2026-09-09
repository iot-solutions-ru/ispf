package com.ispf.server.alert;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.bus.ObjectChangeAsyncHandler;
import org.springframework.stereotype.Component;

@Component
public class AlertRuleListener implements ObjectChangeAsyncHandler {

    private final AlertRuleService alertRuleService;
    private final ObjectManager objectManager;

    public AlertRuleListener(AlertRuleService alertRuleService, ObjectManager objectManager) {
        this.alertRuleService = alertRuleService;
        this.objectManager = objectManager;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void handle(ObjectChangeEvent event) {
        if (!objectManager.isInitialized()) {
            return;
        }
        if (!event.automationEligible()) {
            return;
        }
        if (event.type() != ObjectChangeType.VARIABLE_UPDATED || event.variableName() == null) {
            return;
        }
        alertRuleService.processVariableChange(event.path(), event.variableName());
    }
}
