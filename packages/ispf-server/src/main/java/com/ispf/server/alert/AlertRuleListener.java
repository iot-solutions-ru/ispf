package com.ispf.server.alert;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.bus.ObjectChangeAsyncHandler;
import org.springframework.stereotype.Component;

@Component
public class AlertRuleListener implements ObjectChangeAsyncHandler {

    private final AlertRuleService alertRuleService;

    public AlertRuleListener(AlertRuleService alertRuleService) {
        this.alertRuleService = alertRuleService;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void handle(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.VARIABLE_UPDATED || event.variableName() == null) {
            return;
        }
        alertRuleService.processVariableChange(event.path(), event.variableName());
    }
}
