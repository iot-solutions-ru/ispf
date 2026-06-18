package com.ispf.server.plugin.oilterminal;

import com.ispf.plugin.oilterminal.OilTerminalConstants;
import com.ispf.server.alert.AlertRuleService;
import com.ispf.server.object.ObjectManager;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class OilTerminalAlertBootstrap {

    private static final String RULE_NAME = "Oil tank level low (rvs3)";

    private final AlertRuleService alertRuleService;
    private final ObjectManager objectManager;

    public OilTerminalAlertBootstrap(AlertRuleService alertRuleService, ObjectManager objectManager) {
        this.alertRuleService = alertRuleService;
        this.objectManager = objectManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(110)
    public void seedOilTerminalRules() {
        if (alertRuleService.list().stream().anyMatch(rule -> rule.name().equals(RULE_NAME))) {
            return;
        }
        String tankPath = OilTerminalConstants.tankPath(OilTerminalConstants.DEMO_TANK);
        if (objectManager.tree().findByPath(tankPath).isEmpty()) {
            return;
        }
        alertRuleService.create(new AlertRuleService.CreateAlertRuleRequest(
                RULE_NAME,
                tankPath,
                "levelLow",
                "self.levelLow[\"value\"] == true",
                OilTerminalConstants.EVENT_TANK_LEVEL_LOW,
                "levelM3",
                true,
                true
        ));
    }
}
