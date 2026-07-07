package com.ispf.plugin.workflow;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EscalationTemplateSmokeTest {

    @Test
    void ackTimeoutTemplateEscalatesOnBoundaryTimer() throws Exception {
        String bpmnXml;
        try (InputStream in = getClass().getResourceAsStream("/ack-timeout-escalation.bpmn.xml")) {
            assertThat(in).isNotNull();
            bpmnXml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(bpmnXml);
        WorkflowInstance instance = engine.start("root.workflows.ack-timeout-escalation", process);

        engine.runToCompletion(
                instance,
                process,
                (task, ignored) -> { },
                expr -> true
        );

        assertThat(instance.status()).isEqualTo(InstanceStatus.WAITING);
        assertThat(process.boundaryTimers()).containsKey("operatorAck");

        long deadline = instance.waitingTokens().getFirst().timerDeadlineEpochMs();
        assertThat(deadline).isGreaterThan(0L);

        List<String> messages = new ArrayList<>();
        engine.fireDueTimers(
                instance,
                process,
                (task, ignored) -> messages.add(task.parameters().getOrDefault("message", "")),
                (task, ignored) -> { },
                expr -> true,
                deadline
        );

        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(messages).contains("supervisor notified");
        assertThat(messages).doesNotContain("alarm acknowledged in time");
    }
}
