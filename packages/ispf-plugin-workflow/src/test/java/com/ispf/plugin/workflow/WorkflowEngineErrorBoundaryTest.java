package com.ispf.plugin.workflow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineErrorBoundaryTest {

    private static final String CAUGHT_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn">
              <error id="errShort" errorCode="E_LOCK"/>
              <error id="errLong" errorCode="E_LOCKED"/>
              <process id="err-demo" name="Error Demo" isExecutable="true">
                <startEvent id="start"/>
                <serviceTask id="assign" name="Assign" ispf:action="log" ispf:message="Assign"/>
                <serviceTask id="ok" name="Ok" ispf:action="log" ispf:message="Ok"/>
                <endEvent id="endOk"/>
                <boundaryEvent id="onShort" attachedToRef="assign">
                  <errorEventDefinition errorRef="errShort"/>
                </boundaryEvent>
                <serviceTask id="wrong" name="Wrong" ispf:action="log" ispf:message="Wrong"/>
                <endEvent id="endWrong"/>
                <boundaryEvent id="onLong" attachedToRef="assign">
                  <errorEventDefinition errorRef="errLong"/>
                </boundaryEvent>
                <serviceTask id="recover" name="Recover" ispf:action="log" ispf:message="Recover"/>
                <endEvent id="endRecover"/>
                <sequenceFlow sourceRef="start" targetRef="assign"/>
                <sequenceFlow sourceRef="assign" targetRef="ok"/>
                <sequenceFlow sourceRef="ok" targetRef="endOk"/>
                <sequenceFlow sourceRef="onShort" targetRef="wrong"/>
                <sequenceFlow sourceRef="wrong" targetRef="endWrong"/>
                <sequenceFlow sourceRef="onLong" targetRef="recover"/>
                <sequenceFlow sourceRef="recover" targetRef="endRecover"/>
              </process>
            </definitions>
            """;

    private static final String UNMATCHED_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn">
              <process id="err-miss" name="Miss" isExecutable="true">
                <startEvent id="start"/>
                <serviceTask id="assign" name="Assign" ispf:action="log" ispf:message="Assign"/>
                <boundaryEvent id="onOther" attachedToRef="assign" ispf:errorCode="E_OTHER"/>
                <serviceTask id="recover" name="Recover" ispf:action="log" ispf:message="Recover"/>
                <endEvent id="end"/>
                <sequenceFlow sourceRef="start" targetRef="assign"/>
                <sequenceFlow sourceRef="assign" targetRef="end"/>
                <sequenceFlow sourceRef="onOther" targetRef="recover"/>
                <sequenceFlow sourceRef="recover" targetRef="end"/>
              </process>
            </definitions>
            """;

    @Test
    void serviceTaskFailureFollowsTheLongestMatchingErrorBoundary() throws Exception {
        List<String> messages = run(CAUGHT_BPMN, "Function assign failed: E_LOCKED");

        assertThat(messages).containsExactly("Assign", "Recover");
    }

    @Test
    void unmatchedErrorCodeFailsTheInstance() throws Exception {
        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(UNMATCHED_BPMN);
        WorkflowInstance instance = engine.start("root.workflows.err-miss", process);

        engine.runToCompletion(
                instance,
                process,
                (task, ignored) -> {
                    if ("assign".equals(task.id())) {
                        throw new WorkflowException("Function assign failed: E_LOCKED");
                    }
                },
                expr -> true
        );

        assertThat(instance.status()).isEqualTo(InstanceStatus.FAILED);
        assertThat(instance.errorMessage()).contains("E_LOCKED");
    }

    @Test
    void caughtErrorPublishesTheCodeOnTheInstance() throws Exception {
        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(CAUGHT_BPMN);
        WorkflowInstance instance = engine.start("root.workflows.err-demo", process);
        List<String> messages = new ArrayList<>();

        engine.runToCompletion(
                instance,
                process,
                (task, ignored) -> {
                    messages.add(task.parameters().getOrDefault("message", ""));
                    if ("assign".equals(task.id())) {
                        throw new WorkflowException("Function assign failed: E_LOCKED");
                    }
                },
                expr -> true
        );

        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(instance.variables().get("errorCode")).isEqualTo("E_LOCKED");
        assertThat(instance.variables().get("errorMessage")).contains("E_LOCKED");
        assertThat(messages).contains("Recover").doesNotContain("Ok", "Wrong");
    }

    @Test
    void blankErrorDefinitionCatchesAnyFailure() throws Exception {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="catch-all" name="Catch all" isExecutable="true">
                    <startEvent id="start"/>
                    <serviceTask id="assign" name="Assign" ispf:action="log" ispf:message="Assign"/>
                    <boundaryEvent id="onOther" attachedToRef="assign" ispf:errorCode="E_OTHER"/>
                    <serviceTask id="miss" name="Miss" ispf:action="log" ispf:message="Miss"/>
                    <endEvent id="endMiss"/>
                    <boundaryEvent id="onAny" attachedToRef="assign">
                      <errorEventDefinition/>
                    </boundaryEvent>
                    <serviceTask id="recover" name="Recover" ispf:action="log" ispf:message="Recover"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="assign"/>
                    <sequenceFlow sourceRef="assign" targetRef="end"/>
                    <sequenceFlow sourceRef="onOther" targetRef="miss"/>
                    <sequenceFlow sourceRef="miss" targetRef="endMiss"/>
                    <sequenceFlow sourceRef="onAny" targetRef="recover"/>
                    <sequenceFlow sourceRef="recover" targetRef="end"/>
                  </process>
                </definitions>
                """;

        assertThat(run(bpmn, "boom")).containsExactly("Assign", "Recover");
    }

    @Test
    void messageTaskFailureFollowsTheErrorBoundary() throws Exception {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="msg-err" name="Msg" isExecutable="true">
                    <startEvent id="start"/>
                    <messageTask id="send" name="Send" ispf:message="Send"/>
                    <boundaryEvent id="onFail" attachedToRef="send" ispf:errorCode="E_LOCKED"/>
                    <serviceTask id="recover" name="Recover" ispf:action="log" ispf:message="Recover"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="send"/>
                    <sequenceFlow sourceRef="send" targetRef="end"/>
                    <sequenceFlow sourceRef="onFail" targetRef="recover"/>
                    <sequenceFlow sourceRef="recover" targetRef="end"/>
                  </process>
                </definitions>
                """;
        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(bpmn);
        WorkflowInstance instance = engine.start("root.workflows.msg-err", process);
        List<String> messages = new ArrayList<>();

        engine.runToCompletion(
                instance,
                process,
                (task, ignored) -> messages.add(task.parameters().getOrDefault("message", "")),
                (task, ignored) -> {
                    throw new WorkflowException("publish failed: E_LOCKED");
                },
                expr -> true
        );

        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(messages).containsExactly("Recover");
    }

    @Test
    void laterCallActivityFailureFollowsTheErrorBoundary() throws Exception {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="call-err" name="Call" isExecutable="true">
                    <startEvent id="start"/>
                    <callActivity id="child" name="Child" ispf:workflowPath="root.platform.workflows.child"/>
                    <boundaryEvent id="onFail" attachedToRef="child" ispf:errorCode="E_LOCKED"/>
                    <serviceTask id="recover" name="Recover" ispf:action="log" ispf:message="Recover"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="child"/>
                    <sequenceFlow sourceRef="child" targetRef="end"/>
                    <sequenceFlow sourceRef="onFail" targetRef="recover"/>
                    <sequenceFlow sourceRef="recover" targetRef="end"/>
                  </process>
                </definitions>
                """;
        WorkflowEngine engine = new WorkflowEngine();
        engine.setCallActivityExecutor((call, parent) ->
                new CallActivityExecutor.Result(InstanceStatus.WAITING, "child-1", Map.of(), null));
        BpmnProcess process = engine.parse(bpmn);
        WorkflowInstance instance = engine.start("root.workflows.call-err", process);
        List<String> messages = new ArrayList<>();
        WorkflowActionExecutor executor = (task, ignored) ->
                messages.add(task.parameters().getOrDefault("message", ""));

        engine.runToCompletion(instance, process, executor, expr -> true);
        assertThat(instance.status()).isEqualTo(InstanceStatus.WAITING);

        engine.resumeAfterCallActivityChild(
                instance,
                process,
                "child-1",
                Map.of(),
                "E_LOCKED",
                executor,
                (task, ignored) -> { },
                expr -> true
        );

        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(messages).containsExactly("Recover");
        assertThat(instance.variables().get("errorCode")).isEqualTo("E_LOCKED");
    }

    private static List<String> run(String bpmn, String failure) throws Exception {
        WorkflowEngine engine = new WorkflowEngine();
        BpmnProcess process = engine.parse(bpmn);
        WorkflowInstance instance = engine.start("root.workflows.err-demo", process);
        List<String> messages = new ArrayList<>();
        engine.runToCompletion(
                instance,
                process,
                (task, ignored) -> {
                    messages.add(task.parameters().getOrDefault("message", ""));
                    if ("assign".equals(task.id())) {
                        throw new WorkflowException(failure);
                    }
                },
                expr -> true
        );
        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
        return messages;
    }
}
