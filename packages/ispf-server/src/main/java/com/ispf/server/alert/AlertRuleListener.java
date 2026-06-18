package com.ispf.server.alert;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AlertRuleListener {

    private final AlertRuleService alertRuleService;

    public AlertRuleListener(AlertRuleService alertRuleService) {
        this.alertRuleService = alertRuleService;
    }

    @EventListener
    public void onObjectChange(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.VARIABLE_UPDATED || event.variableName() == null) {
            return;
        }
        alertRuleService.processVariableChange(event.path(), event.variableName());
    }
}
