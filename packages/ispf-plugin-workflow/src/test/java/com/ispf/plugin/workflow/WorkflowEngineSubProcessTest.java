package com.ispf.plugin.workflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineSubProcessTest {

    @Test
    void executesEmbeddedSubProcess() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="sub-demo" name="Subprocess Demo" isExecutable="true">
                    <startEvent id="start"/>
                    <subProcess id="prep" name="Prepare">
                      <startEvent id="subStart"/>
                      <serviceTask id="innerTask" name="Inner log" ispf:action="log" ispf:message="inside"/>
                      <endEvent id="subEnd"/>
                      <sequenceFlow sourceRef="subStart" targetRef="innerTask"/>
                      <sequenceFlow sourceRef="innerTask" targetRef="subEnd"/>
                    </subProcess>
                    <serviceTask id="afterSub" name="After" ispf:action="log" ispf:message="outside"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="prep"/>
                    <sequenceFlow sourceRef="prep" targetRef="afterSub"/>
                    <sequenceFlow sourceRef="afterSub" targetRef="end"/>
                  </process>
                </definitions>
                """;

        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(xml);
        WorkflowInstance instance = engine.start("root.workflows.sub-demo", process);

        engine.runToCompletion(instance, process, (task, ignored) -> { }, expr -> true);

        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(process.subProcesses()).containsKey("prep");
        assertThat(instance.history()).anyMatch(entry -> entry.contains("innerTask"));
        assertThat(instance.history()).anyMatch(entry -> entry.contains("afterSub"));
    }

    @Test
    void parsesSubProcessMetadata() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="meta" name="Meta" isExecutable="true">
                    <startEvent id="start"/>
                    <subProcess id="nested" name="Nested">
                      <startEvent id="nestedStart"/>
                      <endEvent id="nestedEnd"/>
                      <sequenceFlow sourceRef="nestedStart" targetRef="nestedEnd"/>
                    </subProcess>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="nested"/>
                    <sequenceFlow sourceRef="nested" targetRef="end"/>
                  </process>
                </definitions>
                """;

        BpmnProcess process = new BpmnParser().parse(xml);

        assertThat(process.subProcesses().get("nested").startNodeId()).isEqualTo("nestedStart");
        assertThat(process.subProcesses().get("nested").endNodeIds()).containsExactly("nestedEnd");
        assertThat(process.endEventToSubProcess().get("nestedEnd")).isEqualTo("nested");
    }

    @Test
    void executesNestedEmbeddedSubProcess() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="nested-sub" name="Nested Sub" isExecutable="true">
                    <startEvent id="start"/>
                    <subProcess id="outer" name="Outer">
                      <startEvent id="outerStart"/>
                      <subProcess id="inner" name="Inner">
                        <startEvent id="innerStart"/>
                        <serviceTask id="deepTask" name="Deep" ispf:action="log" ispf:message="deep"/>
                        <endEvent id="innerEnd"/>
                        <sequenceFlow sourceRef="innerStart" targetRef="deepTask"/>
                        <sequenceFlow sourceRef="deepTask" targetRef="innerEnd"/>
                      </subProcess>
                      <endEvent id="outerEnd"/>
                      <sequenceFlow sourceRef="outerStart" targetRef="inner"/>
                      <sequenceFlow sourceRef="inner" targetRef="outerEnd"/>
                    </subProcess>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="outer"/>
                    <sequenceFlow sourceRef="outer" targetRef="end"/>
                  </process>
                </definitions>
                """;

        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(xml);
        WorkflowInstance instance = engine.start("root.workflows.nested-sub", process);

        engine.runToCompletion(instance, process, (task, ignored) -> { }, expr -> true);

        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(process.subProcesses()).containsKeys("outer", "inner");
        assertThat(instance.history()).anyMatch(entry -> entry.contains("deepTask"));
    }
}
