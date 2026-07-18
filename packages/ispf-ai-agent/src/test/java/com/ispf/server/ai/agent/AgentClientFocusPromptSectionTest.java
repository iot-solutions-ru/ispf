package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentClientFocusPromptSectionTest {

    @Test
    void formatIncludesSurfaceGuidanceForExpressionEditor() {
        String text = AgentClientFocusPromptSection.format(Map.of(
                "surface", "expression-editor",
                "objectPath", "root.platform.devices.hub",
                "detail", Map.of("expression", "self.x.value > 0")
        ));
        assertThat(text).contains("User UI focus");
        assertThat(text).contains("expression-editor");
        assertThat(text).contains("root.platform.devices.hub");
        assertThat(text).contains("self.x.value > 0");
        assertThat(text).contains("LIVE draft");
    }

    @Test
    void sanitizeDropsBlankFields() {
        Map<String, Object> clean = AgentClientFocusPromptSection.sanitize(Map.of(
                "surface", "dashboard",
                "objectPath", "  ",
                "objectType", "DASHBOARD"
        ));
        assertThat(clean).containsEntry("surface", "dashboard");
        assertThat(clean).containsEntry("objectType", "DASHBOARD");
        assertThat(clean).doesNotContainKey("objectPath");
    }

    @Test
    void formatEmptyWhenNoSurface() {
        assertThat(AgentClientFocusPromptSection.format(Map.of("objectPath", "root"))).isEmpty();
        assertThat(AgentClientFocusPromptSection.format(null)).isEmpty();
    }

    @Test
    void formatChannelDistinguishesCopilotAndStudio() {
        assertThat(AgentClientFocusPromptSection.formatChannel("copilot"))
                .contains("Admin Copilot")
                .contains("not AI Studio");
        assertThat(AgentClientFocusPromptSection.formatChannel("studio"))
                .contains("AI Studio")
                .contains("tree-first");
        assertThat(AgentClientFocusPromptSection.formatChannel(null)).isEmpty();
        assertThat(AgentClientFocusPromptSection.formatChannel("")).isEmpty();
    }

    @Test
    void formatIncludesTrailAndBindingRuleGuidance() {
        String text = AgentClientFocusPromptSection.format(Map.of(
                "surface", "binding-rule",
                "objectPath", "root.platform.devices.virt-cluster.hub",
                "detail", Map.of(
                        "ruleId", "cluster-error",
                        "expression", "self.member1Sine[\"value\"] > 0",
                        "trail", List.of(
                                Map.of("surface", "explorer", "label", "hub", "objectPath", "root.platform.devices.virt-cluster.hub"),
                                Map.of("surface", "properties", "label", "properties/computations"),
                                Map.of("surface", "binding-rule", "label", "rule:cluster-error")
                        )
                )
        ));
        assertThat(text).contains("binding-rule");
        assertThat(text).contains("cluster-error");
        assertThat(text).contains("navigation trail");
        assertThat(text).contains("THE rule");
    }

    @Test
    void formatUserTurnPrefixIncludesTrail() {
        String prefix = AgentClientFocusPromptSection.formatUserTurnPrefix(
                "copilot",
                Map.of(
                        "surface", "expression-editor",
                        "objectPath", "root.platform.devices.virt-cluster.hub",
                        "detail", Map.of(
                                "expression", "self.a > 0",
                                "ruleId", "cluster-error",
                                "trail", List.of(
                                        Map.of("surface", "binding-rule", "label", "rule:cluster-error")
                                )
                        )
                )
        );
        assertThat(prefix).contains("trail:");
        assertThat(prefix).contains("rule:cluster-error");
        assertThat(prefix).contains("EXPRESSION:");
        assertThat(prefix).contains("self.a > 0");
    }

    @Test
    void formatLiveSnapshotReminderOverridesClarifyHistory() {
        String snap = AgentClientFocusPromptSection.formatLiveSnapshotReminder(
                "copilot",
                Map.of(
                        "surface", "expression-editor",
                        "objectPath", "root.platform.devices.virt-cluster.hub",
                        "detail", Map.of(
                                "expression", "self.member1Sine[\"value\"] > 0",
                                "ruleId", "cluster-error"
                        )
                )
        );
        assertThat(snap).contains("LIVE UI SNAPSHOT");
        assertThat(snap).contains("cluster-error");
        assertThat(snap).contains("EXPRESSION:");
        assertThat(snap).contains("SELECTED OBJECT");
        assertThat(snap).contains("FORBIDDEN");
    }

    @Test
    void formatBindingSurfaceListsRulesAndForbidsPathAsk() {
        String snap = AgentClientFocusPromptSection.formatLiveSnapshotReminder(
                "copilot",
                Map.of(
                        "surface", "binding",
                        "objectPath", "root.platform.devices.virt-cluster.hub",
                        "detail", Map.of(
                                "inspectorTab", "computations",
                                "rules", List.of(
                                        Map.of(
                                                "id", "cluster-error",
                                                "target", "clusterError",
                                                "expression", "self.member1sine[\"value\"] > 0"
                                        )
                                )
                        )
                )
        );
        assertThat(snap).contains("SELECTED OBJECT");
        assertThat(snap).contains("root.platform.devices.virt-cluster.hub");
        assertThat(snap).contains("cluster-error");
        assertThat(snap).contains("clusterError");
        assertThat(snap).contains("rules on this object");

        String focus = AgentClientFocusPromptSection.format(Map.of(
                "surface", "binding",
                "objectPath", "root.platform.devices.virt-cluster.hub",
                "detail", Map.of(
                        "rules", List.of(
                                Map.of("id", "member1-sine", "target", "member1sine", "expression", "read(...)")
                        )
                )
        ));
        assertThat(focus).contains("member1-sine");
        assertThat(focus).contains("do not ask which object");

        String prefix = AgentClientFocusPromptSection.formatUserTurnPrefix(
                "copilot",
                Map.of(
                        "surface", "binding",
                        "objectPath", "root.platform.devices.virt-cluster.hub",
                        "detail", Map.of(
                                "rules", List.of(
                                        Map.of("id", "cluster-error", "target", "clusterError", "expression", "x > 0")
                                )
                        )
                )
        );
        assertThat(prefix).contains("SELECTED OBJECT: root.platform.devices.virt-cluster.hub");
        assertThat(prefix).contains("cluster-error");
    }
}
