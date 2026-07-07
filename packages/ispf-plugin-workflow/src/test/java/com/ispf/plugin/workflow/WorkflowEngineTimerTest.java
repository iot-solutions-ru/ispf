package com.ispf.plugin.workflow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineTimerTest {

    private static final String TIMER_CATCH_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn">
              <process id="timer-demo" name="Timer Demo" isExecutable="true">
                <startEvent id="start"/>
                <intermediateCatchEvent id="waitDelay" ispf:durationSeconds="0"/>
                <serviceTask id="done" name="Done" ispf:action="log" ispf:message="Timer fired"/>
                <endEvent id="end"/>
                <sequenceFlow sourceRef="start" targetRef="waitDelay"/>
                <sequenceFlow sourceRef="waitDelay" targetRef="done"/>
                <sequenceFlow sourceRef="done" targetRef="end"/>
              </process>
            </definitions>
            """;

    private static final String BOUNDARY_TIMER_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn">
              <process id="boundary-demo" name="Boundary Demo" isExecutable="true">
                <startEvent id="start"/>
                <userTask id="ackTask" name="Ack" ispf:title="Ack alarm"/>
                <serviceTask id="normal" name="Normal" ispf:action="log" ispf:message="Acked"/>
                <endEvent id="endNormal"/>
                <boundaryEvent id="ackTimeout" attachedToRef="ackTask" cancelActivity="true"
                               ispf:durationSeconds="0"/>
                <serviceTask id="escalate" name="Escalate" ispf:action="log" ispf:message="Escalated"/>
                <endEvent id="endEscalate"/>
                <sequenceFlow sourceRef="start" targetRef="ackTask"/>
                <sequenceFlow sourceRef="ackTask" targetRef="normal"/>
                <sequenceFlow sourceRef="normal" targetRef="endNormal"/>
                <sequenceFlow sourceRef="ackTimeout" targetRef="escalate"/>
                <sequenceFlow sourceRef="escalate" targetRef="endEscalate"/>
              </process>
            </definitions>
            """;

    @Test
    void waitsAtTimerCatchUntilTimerFires() throws Exception {
        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(TIMER_CATCH_BPMN);
        WorkflowInstance instance = engine.start("root.workflows.timer-demo", process);

        engine.step(instance, process, (task, ignored) -> { }, expr -> true);
        engine.step(instance, process, (task, ignored) -> { }, expr -> true);

        assertThat(instance.status()).isEqualTo(InstanceStatus.WAITING);
        assertThat(instance.waitingTokens().getFirst().pendingTimerCatchNodeId()).isEqualTo("waitDelay");

        List<String> messages = new ArrayList<>();
        engine.fireDueTimers(
                instance,
                process,
                (task, ignored) -> messages.add(task.parameters().getOrDefault("message", "")),
                (task, ignored) -> { },
                expr -> true
        );

        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(messages).contains("Timer fired");
    }

    @Test
    void boundaryTimerEscalatesWhenUserTaskTimesOut() throws Exception {
        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(BOUNDARY_TIMER_BPMN);
        WorkflowInstance instance = engine.start("root.workflows.boundary-demo", process);

        engine.runToCompletion(
                instance,
                process,
                (task, ignored) -> { },
                expr -> true
        );

        assertThat(instance.status()).isEqualTo(InstanceStatus.WAITING);
        assertThat(instance.pendingUserTaskId()).contains("ackTask");
        assertThat(instance.waitingTokens().getFirst().pendingBoundaryTimerNodeId()).isEqualTo("ackTimeout");

        List<String> messages = new ArrayList<>();
        engine.fireDueTimers(
                instance,
                process,
                (task, ignored) -> messages.add(task.parameters().getOrDefault("message", "")),
                (task, ignored) -> { },
                expr -> true
        );

        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(messages).contains("Escalated");
        assertThat(messages).doesNotContain("Acked");
    }

    @Test
    void userTaskCompletionCancelsBoundaryTimer() throws Exception {
        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(BOUNDARY_TIMER_BPMN);
        WorkflowInstance instance = engine.start("root.workflows.boundary-demo", process);

        engine.runToCompletion(
                instance,
                process,
                (task, ignored) -> { },
                expr -> true
        );

        List<String> messages = new ArrayList<>();
        engine.completeUserTask(
                instance,
                process,
                "ackTask",
                (task, ignored) -> messages.add(task.parameters().getOrDefault("message", "")),
                (task, ignored) -> { },
                expr -> true
        );

        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(messages).contains("Acked");
        assertThat(messages).doesNotContain("Escalated");
    }
}
