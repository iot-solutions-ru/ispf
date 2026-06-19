package com.ispf.plugin.workflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineSignalTest {

    private static final String SIGNAL_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn">
              <process id="signal-demo" name="Signal Demo" isExecutable="true">
                <startEvent id="start"/>
                <intermediateCatchEvent id="waitIncident" ispf:signal="incidentRegistered"/>
                <serviceTask id="done" name="Done" ispf:action="log" ispf:message="Resumed"/>
                <endEvent id="end"/>
                <sequenceFlow sourceRef="start" targetRef="waitIncident"/>
                <sequenceFlow sourceRef="waitIncident" targetRef="done"/>
                <sequenceFlow sourceRef="done" targetRef="end"/>
              </process>
            </definitions>
            """;

    @Test
    void waitsAtSignalCatchUntilSignalDelivered() throws Exception {
        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(SIGNAL_BPMN);
        WorkflowInstance instance = engine.start("root.workflows.signal-demo", process);

        engine.step(instance, process, (task, ignored) -> { }, expr -> true);
        engine.step(instance, process, (task, ignored) -> { }, expr -> true);

        assertThat(instance.status()).isEqualTo(InstanceStatus.WAITING);
        assertThat(instance.pendingSignalName()).contains("incidentRegistered");

        engine.deliverSignal(instance, process, "incidentRegistered", (task, ignored) -> { }, (task, ignored) -> { }, expr -> true);

        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
    }
}
