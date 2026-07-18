package com.ispf.plugin.workflow;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowEngineMessageTest {

    private static final String MESSAGE_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn">
              <process id="message-demo" name="Message Demo" isExecutable="true">
                <startEvent id="start"/>
                <intermediateCatchEvent id="waitAck" name="Wait ERP ack">
                  <messageEventDefinition messageRef="erpAck"/>
                </intermediateCatchEvent>
                <serviceTask id="done" name="Done" ispf:action="log" ispf:message="Ack received"/>
                <endEvent id="end"/>
                <sequenceFlow sourceRef="start" targetRef="waitAck"/>
                <sequenceFlow sourceRef="waitAck" targetRef="done"/>
                <sequenceFlow sourceRef="done" targetRef="end"/>
              </process>
            </definitions>
            """;

    @Test
    void waitsAtMessageCatchUntilMessageDelivered() throws Exception {
        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(MESSAGE_BPMN);
        WorkflowInstance instance = engine.start("root.workflows.message-demo", process);

        engine.step(instance, process, (task, ignored) -> { }, expr -> true);
        engine.step(instance, process, (task, ignored) -> { }, expr -> true);

        assertThat(instance.status()).isEqualTo(InstanceStatus.WAITING);
        assertThat(instance.pendingMessageName()).contains("erpAck");
        assertThat(instance.pendingSignalName()).isEmpty();

        engine.deliverMessage(
                instance,
                process,
                "erpAck",
                (task, ignored) -> { },
                (task, ignored) -> { },
                expr -> true
        );

        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
    }

    @Test
    void executesMessageThrowEvent() throws Exception {
        String throwBpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="throw-demo" name="Throw Demo" isExecutable="true">
                    <startEvent id="start"/>
                    <intermediateThrowEvent id="throwAck" name="Send ack">
                      <messageEventDefinition messageRef="erpAck"/>
                    </intermediateThrowEvent>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="throwAck"/>
                    <sequenceFlow sourceRef="throwAck" targetRef="end"/>
                  </process>
                </definitions>
                """;

        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(throwBpmn);
        WorkflowInstance instance = engine.start("root.workflows.throw-demo", process);
        List<String> thrown = new ArrayList<>();

        engine.runToCompletion(
                instance,
                process,
                (task, ignored) -> { },
                (task, ignored) -> {
                    if ("bpmn-throw".equals(task.channel())) {
                        thrown.add(task.subject());
                    }
                },
                expr -> true
        );

        assertThat(thrown).containsExactly("erpAck");
        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
    }

    @Test
    void rejectsWrongMessageNameOnDeliver() throws Exception {
        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(MESSAGE_BPMN);
        WorkflowInstance instance = engine.start("root.workflows.message-demo", process);

        engine.step(instance, process, (task, ignored) -> { }, expr -> true);
        engine.step(instance, process, (task, ignored) -> { }, expr -> true);
        assertThat(instance.status()).isEqualTo(InstanceStatus.WAITING);

        assertThatThrownBy(() -> engine.deliverMessage(
                instance,
                process,
                "wrongMessage",
                (task, ignored) -> { },
                (task, ignored) -> { },
                expr -> true
        )).isInstanceOf(WorkflowException.class)
                .hasMessageContaining("wrongMessage");
    }
}
