package com.ispf.plugin.workflow;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineCallActivityTest {

    private static final String PARENT_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn">
              <process id="parent" name="Parent" isExecutable="true">
                <startEvent id="start"/>
                <callActivity id="call" name="Child" ispf:workflowPath="root.platform.workflows.child"/>
                <endEvent id="end"/>
                <sequenceFlow sourceRef="start" targetRef="call"/>
                <sequenceFlow sourceRef="call" targetRef="end"/>
              </process>
            </definitions>
            """;

    @Test
    void completesWhenChildCompletesSynchronously() throws Exception {
        WorkflowEngine engine = new WorkflowEngine();
        AtomicInteger starts = new AtomicInteger();
        engine.setCallActivityExecutor((call, parent) -> {
            starts.incrementAndGet();
            assertThat(call.workflowPath()).isEqualTo("root.platform.workflows.child");
            return new CallActivityExecutor.Result(
                    InstanceStatus.COMPLETED,
                    "child-1",
                    Map.of("result", "ok"),
                    null
            );
        });

        BpmnProcess process = engine.parse(PARENT_XML);
        WorkflowInstance instance = engine.start("root.platform.workflows.parent", process);
        engine.runToCompletion(instance, process, (task, ignored) -> { }, expr -> true);

        assertThat(starts.get()).isEqualTo(1);
        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(instance.variables().get("call.call.result")).isEqualTo("ok");
    }

    @Test
    void waitsAndResumesWhenChildIsAsync() throws Exception {
        WorkflowEngine engine = new WorkflowEngine();
        engine.setCallActivityExecutor((call, parent) -> new CallActivityExecutor.Result(
                InstanceStatus.WAITING,
                "child-async",
                Map.of(),
                null
        ));

        BpmnProcess process = engine.parse(PARENT_XML);
        WorkflowInstance instance = engine.start("root.platform.workflows.parent", process);
        engine.step(instance, process, (task, ignored) -> { }, expr -> true);

        assertThat(instance.status()).isEqualTo(InstanceStatus.WAITING);
        assertThat(instance.pendingCallChildInstanceId()).contains("child-async");

        engine.resumeAfterCallActivityChild(
                instance,
                process,
                "child-async",
                Map.of("done", "1"),
                null,
                (task, ignored) -> { },
                (task, ignored) -> { },
                expr -> true
        );

        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(instance.variables().get("call.call.done")).isEqualTo("1");
    }

    @Test
    void failsParentWhenChildFails() throws Exception {
        WorkflowEngine engine = new WorkflowEngine();
        engine.setCallActivityExecutor((call, parent) -> new CallActivityExecutor.Result(
                InstanceStatus.FAILED,
                "child-fail",
                Map.of(),
                "boom"
        ));

        BpmnProcess process = engine.parse(PARENT_XML);
        WorkflowInstance instance = engine.start("root.platform.workflows.parent", process);
        engine.step(instance, process, (task, ignored) -> { }, expr -> true);

        assertThat(instance.status()).isEqualTo(InstanceStatus.FAILED);
        assertThat(instance.errorMessage()).contains("boom");
    }
}
