package com.ispf.plugin.workflow;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineV3Test {

    @Test
    void executesMessageTaskAndParallelBranches() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="demo" name="Demo" isExecutable="true">
                    <startEvent id="start"/>
                    <parallelGateway id="fork"/>
                    <serviceTask id="branchA" name="Branch A" ispf:action="log" ispf:message="A"/>
                    <messageTask id="branchB" name="Branch B"
                                 ispf:subject="ispf.test.message"
                                 ispf:message="hello"/>
                    <parallelGateway id="join"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="fork"/>
                    <sequenceFlow sourceRef="fork" targetRef="branchA"/>
                    <sequenceFlow sourceRef="fork" targetRef="branchB"/>
                    <sequenceFlow sourceRef="branchA" targetRef="join"/>
                    <sequenceFlow sourceRef="branchB" targetRef="join"/>
                    <sequenceFlow sourceRef="join" targetRef="end"/>
                  </process>
                </definitions>
                """;

        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(xml);
        WorkflowInstance instance = engine.start("root.workflows.demo", process);

        AtomicInteger messages = new AtomicInteger();
        engine.step(instance, process, (task, ignored) -> { }, (task, ignored) -> messages.incrementAndGet(), expr -> true);
        engine.step(instance, process, (task, ignored) -> { }, (task, ignored) -> messages.incrementAndGet(), expr -> true);

        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(messages.get()).isEqualTo(1);
        assertThat(instance.history()).anyMatch(entry -> entry.contains("branchA"));
        assertThat(instance.history()).anyMatch(entry -> entry.contains("branchB"));
    }

    @Test
    void parsesMessageTaskAttributes() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="demo" name="Demo" isExecutable="true">
                    <startEvent id="start"/>
                    <messageTask id="msg" name="Notify"
                                 ispf:subject="ispf.ops.alarm"
                                 ispf:message="Alarm"
                                 ispf:channel="nats"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="msg"/>
                    <sequenceFlow sourceRef="msg" targetRef="end"/>
                  </process>
                </definitions>
                """;

        BpmnProcess process = new WorkflowEngine().parse(xml);
        MessageTaskDefinition message = process.messageTasks().get("msg");
        assertThat(message).isNotNull();
        assertThat(message.subject()).isEqualTo("ispf.ops.alarm");
        assertThat(message.channel()).isEqualTo("nats");
    }
}
