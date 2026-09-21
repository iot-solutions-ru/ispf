package com.ispf.server.ai.agent;

import com.ispf.server.alert.AlertRuleService;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.script.PlatformScriptBridge;
import com.ispf.server.application.test.FunctionTestRunner;
import com.ispf.server.event.EventJournalStore;
import com.ispf.server.object.ObjectTreePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTestToolsTest {

    @Mock
    private FunctionTestRunner functionTestRunner;
    @Mock
    private ApplicationBundleDeployService bundleDeployService;
    @Mock
    private PlatformScriptBridge platformScriptBridge;
    @Mock
    private AlertRuleService alertRuleService;
    @Mock
    private EventJournalStore eventJournalStore;
    @Mock
    private ObjectTreePort objectTreePort;

    private PlatformAgentTool tool(String name) {
        return AgentTestTools.all(
                functionTestRunner,
                bundleDeployService,
                platformScriptBridge,
                alertRuleService,
                eventJournalStore,
                objectTreePort
        ).stream().filter(t -> name.equals(t.name())).findFirst().orElseThrow();
    }

    @Test
    void testFunctionReturnsPassFromRunner() throws Exception {
        when(functionTestRunner.run(any())).thenReturn(new FunctionTestRunner.TestResult(
                "demo-ping",
                "function",
                "PASS",
                List.of(),
                Map.of("rowCount", 1)
        ));
        Map<String, Object> result = tool("test_function").execute(
                Map.of(
                        "objectPath", "root.platform.singleton-blueprints.demo-app-hub-v1",
                        "functionName", "demo_ping",
                        "expect", Map.of("errorCode", "OK")
                ),
                new AgentContext("admin", null, null)
        );
        assertThat(result.get("status")).isEqualTo("PASS");
        assertThat(AgentToolInputSchemas.hasCatalogEntry("test_function")).isTrue();
        assertThat(AgentToolPackCatalog.packFor("test_function")).isEqualTo(AgentToolPackCatalog.AUTOMATION);
        assertThat(AgentToolPackCatalog.packFor("run_bundle_tests")).isEqualTo(AgentToolPackCatalog.BUNDLES);
    }

    @Test
    void judgeRequiresPassForApplicationBundle() {
        var approve = AgentJudgeService.evaluate(
                List.of(Map.of(
                        "type", "tool",
                        "tool", "test_function",
                        "result", Map.of("status", "PASS")
                )),
                new AgentRunState(),
                Map.of("assignmentType", "application_bundle"),
                "deploy demo app"
        );
        assertThat(approve.verdict()).isEqualTo(AgentJudgeService.Verdict.APPROVE);

        var rework = AgentJudgeService.evaluate(
                List.of(Map.of(
                        "type", "tool",
                        "tool", "test_function",
                        "result", Map.of("status", "FAIL", "errors", List.of("rowCount"))
                )),
                new AgentRunState(),
                Map.of("assignmentType", "application_bundle"),
                "deploy demo app"
        );
        assertThat(rework.verdict()).isEqualTo(AgentJudgeService.Verdict.REWORK);
    }
}
