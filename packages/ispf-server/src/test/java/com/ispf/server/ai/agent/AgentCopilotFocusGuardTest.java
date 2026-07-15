package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCopilotFocusGuardTest {

    @Test
    void rejectsAskingForExpressionWhenDraftPresent() {
        var reject = AgentCopilotFocusGuard.rejectClarifyFinish(
                "copilot",
                Map.of(
                        "surface", "expression-editor",
                        "objectPath", "root.platform.devices.dev-01",
                        "detail", Map.of(
                                "expression", "self.intValue.value + self.floatValue.value",
                                "ruleId", "sum-int-float"
                        )
                ),
                """
                        Вы не указали, какое именно CEL-выражение вас интересует.
                        Скопируйте само выражение сюда.
                        """
        );
        assertThat(reject).isPresent();
        assertThat(reject.get()).contains("self.intValue.value + self.floatValue.value");
        assertThat(reject.get()).contains("sum-int-float");
    }

    @Test
    void rejectsScreenClarifyWhenObjectPathPresent() {
        var reject = AgentCopilotFocusGuard.rejectClarifyFinish(
                "copilot",
                Map.of(
                        "surface", "binding",
                        "objectPath", "root.platform.devices.virt-cluster.hub",
                        "detail", Map.of(
                                "rules", List.of(
                                        Map.of("id", "cluster-error", "target", "clusterError", "expression", "x > 0")
                                )
                        )
                ),
                "Вы не указали, какой именно экран. Используйте list_binding_rules path=..."
        );
        assertThat(reject).isPresent();
        assertThat(reject.get()).contains("cluster-error");
        assertThat(reject.get()).contains("virt-cluster.hub");
    }

    @Test
    void rejectsDescribeScreenWhenSystemSettingsFocused() {
        var reject = AgentCopilotFocusGuard.rejectClarifyFinish(
                "copilot",
                Map.of(
                        "surface", "system",
                        "detail", Map.of(
                                "screenTitle", "System › Settings",
                                "systemTab", "settings",
                                "settingsTab", "integrations",
                                "visibleSettingIds", List.of("redis.enabled", "ai.enabled")
                        )
                ),
                "Чтобы объяснить экран, опишите его или скопируйте содержимое с UI."
        );
        assertThat(reject).isPresent();
        assertThat(reject.get()).contains("integrations");
        assertThat(reject.get()).contains("System › Settings");
    }

    @Test
    void allowsFinishThatMentionsTheExpression() {
        var reject = AgentCopilotFocusGuard.rejectClarifyFinish(
                "copilot",
                Map.of(
                        "surface", "expression-editor",
                        "detail", Map.of("expression", "self.intValue.value + self.floatValue.value")
                ),
                "Выражение `self.intValue.value + self.floatValue.value` складывает int и float."
        );
        assertThat(reject).isEmpty();
    }

    @Test
    void rejectsStudioModeAdvice() {
        var reject = AgentCopilotFocusGuard.rejectClarifyFinish(
                "copilot",
                Map.of(
                        "surface", "mimic",
                        "objectPath", "root.platform.mimics.test",
                        "detail", Map.of("screenTitle", "SCADA Mimic test", "elementCount", 0)
                ),
                "Для редактирования нужно переключиться в режим Execute — в Ask-режиме можно только читать."
        );
        assertThat(reject).isPresent();
        assertThat(reject.get()).contains("Ask/Plan/Execute");
    }

    @Test
    void alreadyNudgedDetectsGuardStep() {
        assertThat(AgentCopilotFocusGuard.alreadyNudged(List.of())).isFalse();
        assertThat(AgentCopilotFocusGuard.alreadyNudged(List.of(
                Map.of("type", "guard", "copilotFocusNudge", true)
        ))).isTrue();
    }
}
