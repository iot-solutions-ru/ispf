package com.ispf.server.automation;

import com.ispf.server.object.PlatformObjectReadinessGate;
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
    @Order(PlatformObjectReadinessGate.AFTER_OBJECT_TREE_READY_ORDER)
    public void rebuildAfterTreeLoaded() {
        ruleIndex.rebuild();
    }
}
