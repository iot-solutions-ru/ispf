package com.ispf.server.automation;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class AutomationRuleIndexStartup {

    private final AutomationRuleIndex ruleIndex;

    public AutomationRuleIndexStartup(AutomationRuleIndex ruleIndex) {
        this.ruleIndex = ruleIndex;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
    public void rebuildAfterTreeLoaded() {
        ruleIndex.rebuild();
    }
}
