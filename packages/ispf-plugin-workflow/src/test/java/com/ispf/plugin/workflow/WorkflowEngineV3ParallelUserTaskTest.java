package com.ispf.plugin.workflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineV3ParallelUserTaskTest {

    @Test
    void parallelBranchesCanContainUserTasks() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="demo" name="Demo" isExecutable="true">
                    <startEvent id="start"/>
                    <parallelGateway id="fork"/>
                    <userTask id="approveA" name="Approve A" ispf:title="Branch A approval"/>
                    <serviceTask id="branchB" name="Branch B" ispf:action="log" ispf:message="B"/>
                    <parallelGateway id="join"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="fork"/>
                    <sequenceFlow sourceRef="fork" targetRef="approveA"/>
                    <sequenceFlow sourceRef="fork" targetRef="branchB"/>
                    <sequenceFlow sourceRef="approveA" targetRef="join"/>
                    <sequenceFlow sourceRef="branchB" targetRef="join"/>
                    <sequenceFlow sourceRef="join" targetRef="end"/>
                  </process>
                </definitions>
                """;

        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(xml);
        WorkflowInstance instance = engine.start("root.workflows.demo", process);

        engine.step(instance, process, (task, ignored) -> { }, expr -> true);
        engine.step(instance, process, (task, ignored) -> { }, expr -> true);

        assertThat(instance.status()).isEqualTo(InstanceStatus.WAITING);
        assertThat(instance.pendingUserTaskIds()).containsExactly("approveA");

        engine.completeUserTask(instance, process, "approveA", (task, ignored) -> { }, expr -> true);
        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(instance.history()).anyMatch(entry -> entry.contains("branchB"));
        assertThat(instance.history()).anyMatch(entry -> entry.contains("JOIN@join"));
    }
}
