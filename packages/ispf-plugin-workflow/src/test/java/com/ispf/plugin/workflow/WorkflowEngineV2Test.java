package com.ispf.plugin.workflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineV2Test {

    @Test
    void waitsAtUserTaskWhenGatewayConditionMatches() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="demo" name="Demo" isExecutable="true">
                    <startEvent id="start"/>
                    <exclusiveGateway id="gateway"/>
                    <userTask id="approve" name="Approve" ispf:title="Approve alarm"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="gateway"/>
                    <sequenceFlow sourceRef="gateway" targetRef="approve" ispf:condition="needsApproval"/>
                    <sequenceFlow sourceRef="gateway" targetRef="end" ispf:default="true"/>
                    <sequenceFlow sourceRef="approve" targetRef="end"/>
                  </process>
                </definitions>
                """;

        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(xml);
        WorkflowInstance instance = engine.start("root.workflows.demo", process);

        engine.step(instance, process, (task, ignored) -> { }, expr -> "needsApproval".equals(expr));
        engine.step(instance, process, (task, ignored) -> { }, expr -> "needsApproval".equals(expr));
        engine.step(instance, process, (task, ignored) -> { }, expr -> "needsApproval".equals(expr));

        assertThat(instance.status()).isEqualTo(InstanceStatus.WAITING);
        assertThat(instance.pendingUserTaskId()).contains("approve");
    }

    @Test
    void completesAfterUserTaskResume() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="demo" name="Demo" isExecutable="true">
                    <startEvent id="start"/>
                    <userTask id="approve" name="Approve"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="approve"/>
                    <sequenceFlow sourceRef="approve" targetRef="end"/>
                  </process>
                </definitions>
                """;

        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(xml);
        WorkflowInstance instance = engine.start("root.workflows.demo", process);
        engine.step(instance, process, (task, ignored) -> { }, expr -> true);
        engine.step(instance, process, (task, ignored) -> { }, expr -> true);
        assertThat(instance.status()).isEqualTo(InstanceStatus.WAITING);

        engine.completeUserTask(instance, process, (task, ignored) -> { }, expr -> true);
        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
    }
}
