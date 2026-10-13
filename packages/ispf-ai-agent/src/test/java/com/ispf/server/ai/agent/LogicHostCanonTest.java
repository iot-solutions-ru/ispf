package com.ispf.server.ai.agent;

import com.ispf.core.object.ObjectType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogicHostCanonTest {

    @Test
    void hostKindSchemaExcludesDeviceAndIsRequired() {
        assertHostKindSchema("deploy_tree_function");
        assertHostKindSchema("deploy_app_function");
    }

    @Test
    void schemaRejectsDeviceHostKindBeforeTheHandler() {
        Map<String, Object> schema = AgentToolInputSchemas.forTool("deploy_tree_function");
        var violation = AgentToolSchemaValidator.validate(schema, Map.of(
                "path", "root.platform.devices.pump",
                "hostKind", "DEVICE",
                "functionName", "tick",
                "sourceType", "script",
                "sourceBody", "{}"
        ));
        assertThat(violation).isPresent();
        assertThat(violation.get().code()).isEqualTo("ARG_ENUM_MISMATCH");
        assertThat(violation.get().path()).isEqualTo("hostKind");
    }

    @Test
    void guardRejectsMissingKindAndLiveDevice() {
        Map<String, Object> missing = LogicHostGuard.rejectDeviceHost("", "root.platform.devices.pump", null);
        assertThat(missing.get("code")).isEqualTo("LOGIC_HOST_DEVICE");
        assertThat(missing.get("status")).isEqualTo("ERROR");

        Map<String, Object> live = LogicHostGuard.rejectDeviceHost(
                "SINGLETON",
                "root.platform.devices.pump",
                ObjectType.DEVICE
        );
        assertThat(live.get("code")).isEqualTo("LOGIC_HOST_DEVICE");
        assertThat(LogicHostGuard.rejectDeviceHost("INSTANCE", "root.platform.singleton-blueprints.hub", null))
                .isNull();
    }

    @Test
    void judgeReworksShippedSolutionWhenLogicHostDeviceIsOnlyAWarning() {
        List<Map<String, Object>> steps = List.of(Map.of(
                "type", "tool",
                "tool", "validate_bundle",
                "result", Map.of(
                        "status", "OK",
                        "issues", List.of(Map.of(
                                "code", "LOGIC_HOST_DEVICE",
                                "severity", "WARNING",
                                "message", "function on DEVICE"
                        ))
                )
        ));
        var judge = AgentJudgeService.evaluate(
                steps,
                new AgentRunState(),
                Map.of("assignmentType", "application_bundle"),
                "ship the solution"
        );
        assertThat(judge.verdict()).isEqualTo(AgentJudgeService.Verdict.REWORK);
        assertThat(judge.issues()).anyMatch(issue -> issue.startsWith("logic.host"));
    }

    @Test
    void authoringValidateWarningDoesNotBlockANonShipTurn() {
        List<Map<String, Object>> steps = List.of(Map.of(
                "type", "tool",
                "tool", "validate_bundle",
                "result", Map.of(
                        "status", "OK",
                        "issues", List.of(Map.of("code", "LOGIC_HOST_DEVICE", "severity", "WARNING"))
                )
        ));
        var judge = AgentJudgeService.evaluate(steps, new AgentRunState(), Map.of(), "list objects");
        assertThat(judge.verdict()).isEqualTo(AgentJudgeService.Verdict.APPROVE);
    }

    @Test
    void importPackageResultWithLogicHostDeviceIsRework() {
        List<Map<String, Object>> steps = List.of(Map.of(
                "type", "tool",
                "tool", "import_package",
                "result", Map.of(
                        "status", "ERROR",
                        "shipCanon", true,
                        "errors", List.of("[LOGIC_HOST_DEVICE] functions[0].objectPath: hosted on DEVICE")
                )
        ));
        var acceptance = AcceptanceVerdict.evaluate(steps, "list objects", null);
        assertThat(acceptance.checks()).extracting(AcceptanceVerdict.Check::id).contains("logic.host");
        assertThat(acceptance.firstBlocking().id()).isIn("errors.none", "logic.host");
        assertThat(acceptance.allowsFinish()).isFalse();

        var judge = AgentJudgeService.evaluate(steps, new AgentRunState(), Map.of(), "list objects");
        assertThat(judge.verdict()).isEqualTo(AgentJudgeService.Verdict.REWORK);
        assertThat(judge.issues()).anyMatch(issue -> issue.contains("LOGIC_HOST_DEVICE") || issue.startsWith("logic.host"));
    }

    @Test
    void acceptanceFailsWhenHostKindDefaultsAwayFromSingleton() {
        var verdict = AcceptanceVerdict.evaluate(List.of(Map.of(
                "type", "tool",
                "tool", "deploy_tree_function",
                "arguments", Map.of("path", "root.platform.devices.pump", "hostKind", "DEVICE"),
                "result", Map.of(
                        "status", "ERROR",
                        "code", "ARG_ENUM_MISMATCH",
                        "path", "hostKind"
                )
        )), "", null);
        assertThat(verdict.allowsFinish()).isFalse();
        assertThat(verdict.checks()).extracting(AcceptanceVerdict.Check::id).contains("logic.host");
        AcceptanceVerdict.Check host = verdict.checks().stream()
                .filter(check -> "logic.host".equals(check.id()))
                .findFirst()
                .orElseThrow();
        assertThat(host.passed()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static void assertHostKindSchema(String tool) {
        Map<String, Object> schema = AgentToolInputSchemas.forTool(tool);
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> hostKind = (Map<String, Object>) properties.get("hostKind");
        assertThat(hostKind.get("enum")).asList().containsExactly("SINGLETON", "INSTANCE");
        assertThat(schema.get("required")).asList().contains("hostKind");
    }
}
