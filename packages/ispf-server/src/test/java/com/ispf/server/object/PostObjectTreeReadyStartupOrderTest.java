package com.ispf.server.object;

import com.ispf.server.automation.AutomationRuleIndexStartup;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.operator.OperatorAppObjectTreeStartupSync;
import com.ispf.server.workflow.WorkflowEventTriggerIndexStartup;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PostObjectTreeReadyStartupOrderTest {

    @Test
    void markObjectTreeReadyRunsBeforePostReadyListeners() throws Exception {
        int readyOrder = orderValue(PlatformObjectReadinessGate.class, "markObjectTreeReady");
        assertThat(readyOrder).isEqualTo(PlatformObjectReadinessGate.OBJECT_TREE_READY_ORDER);

        assertThat(orderValue(DriverRuntimeService.class, "startConfiguredDrivers"))
                .isEqualTo(PlatformObjectReadinessGate.AFTER_OBJECT_TREE_READY_ORDER);
        assertThat(orderValue(OperatorAppObjectTreeStartupSync.class, "syncOperatorAppsIntoObjectTree"))
                .isEqualTo(PlatformObjectReadinessGate.AFTER_OBJECT_TREE_READY_ORDER);
        assertThat(orderValue(WorkflowEventTriggerIndexStartup.class, "rebuildAfterTreeLoaded"))
                .isEqualTo(PlatformObjectReadinessGate.AFTER_OBJECT_TREE_READY_ORDER);
        assertThat(orderValue(AutomationRuleIndexStartup.class, "rebuildAfterTreeLoaded"))
                .isEqualTo(PlatformObjectReadinessGate.AFTER_OBJECT_TREE_READY_ORDER);

        assertThat(readyOrder).isLessThan(orderValue(DriverRuntimeService.class, "startConfiguredDrivers"));
    }

    private static int orderValue(Class<?> type, String methodName) throws Exception {
        Method method = type.getDeclaredMethod(methodName);
        org.springframework.core.annotation.Order order = method.getAnnotation(org.springframework.core.annotation.Order.class);
        assertThat(order).isNotNull();
        return order.value();
    }
}
