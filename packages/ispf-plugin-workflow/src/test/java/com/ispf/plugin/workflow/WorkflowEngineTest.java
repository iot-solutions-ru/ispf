package com.ispf.plugin.workflow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineTest {

    @Test
    void runsProcessToCompletion() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="demo" name="Demo" isExecutable="true">
                    <startEvent id="start"/>
                    <serviceTask id="task" name="Log" ispf:action="log" ispf:message="hello"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="task"/>
                    <sequenceFlow sourceRef="task" targetRef="end"/>
                  </process>
                </definitions>
                """;

        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(xml);
        WorkflowInstance instance = engine.start("root.platform.workflows.demo", process);

        List<String> messages = new ArrayList<>();
        engine.runToCompletion(instance, process, (task, ignored) -> messages.add(task.parameters().get("message")), expr -> true);

        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(messages).containsExactly("hello");
    }
}
