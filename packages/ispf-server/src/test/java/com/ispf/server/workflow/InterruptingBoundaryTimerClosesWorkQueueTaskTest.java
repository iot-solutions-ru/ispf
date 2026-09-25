package com.ispf.server.workflow;

import com.ispf.plugin.workflow.BpmnProcess;
import com.ispf.plugin.workflow.InstanceStatus;
import com.ispf.plugin.workflow.WorkflowEngine;
import com.ispf.plugin.workflow.WorkflowInstance;
import com.ispf.server.persistence.WorkflowUserTaskRepository;
import com.ispf.server.persistence.entity.WorkflowUserTaskEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class InterruptingBoundaryTimerClosesWorkQueueTaskTest {

    private static final String BOUNDARY_TIMER_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn">
              <process id="boundary-wq-demo" name="Boundary WQ Demo" isExecutable="true">
                <startEvent id="start"/>
                <userTask id="ackTask" name="Ack" ispf:title="Ack alarm"/>
                <serviceTask id="normal" name="Normal" ispf:action="log" ispf:message="Acked"/>
                <serviceTask id="escalate" name="Escalate" ispf:action="log" ispf:message="Escalated"/>
                <endEvent id="endNormal"/>
                <endEvent id="endEscalate"/>
                <boundaryEvent id="ackTimeout" attachedToRef="ackTask" cancelActivity="true"
                               ispf:durationSeconds="0"/>
                <sequenceFlow sourceRef="start" targetRef="ackTask"/>
                <sequenceFlow sourceRef="ackTask" targetRef="normal"/>
                <sequenceFlow sourceRef="normal" targetRef="endNormal"/>
                <sequenceFlow sourceRef="ackTimeout" targetRef="escalate"/>
                <sequenceFlow sourceRef="escalate" targetRef="endEscalate"/>
              </process>
            </definitions>
            """;

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private WorkflowInstanceStore instanceStore;

    @Autowired
    private WorkflowUserTaskRepository userTaskRepository;

    @Test
    void interruptingBoundaryTimerClosesOpenWorkQueueTask() throws Exception {
        BpmnProcess process = workflowEngine.parse(BOUNDARY_TIMER_BPMN);
        WorkflowInstance instance = workflowEngine.start(
                "root.platform.workflows.boundary-wq-demo",
                process
        );
        workflowEngine.runToCompletion(
                instance,
                process,
                (task, ignored) -> { },
                expr -> true
        );
        assertThat(instance.status()).isEqualTo(InstanceStatus.WAITING);
        assertThat(instance.pendingUserTaskId()).contains("ackTask");

        instanceStore.save(instance, process, null, process.userTasks().get("ackTask"));
        WorkflowUserTaskEntity open = userTaskRepository
                .findByInstanceIdAndTaskNodeIdAndStatus(instance.instanceId(), "ackTask", "OPEN")
                .orElseThrow();
        assertThat(open.getStatus()).isEqualTo("OPEN");

        List<String> messages = new ArrayList<>();
        workflowEngine.fireDueTimers(
                instance,
                process,
                (task, ignored) -> messages.add(task.parameters().getOrDefault("message", "")),
                (task, ignored) -> { },
                expr -> true
        );
        assertThat(instance.status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(messages).contains("Escalated");
        assertThat(instance.pendingUserTaskId()).isEmpty();

        instanceStore.save(instance, process, null, null);

        WorkflowUserTaskEntity closed = userTaskRepository.findById(open.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo("COMPLETED");
        assertThat(closed.getCompletedAt()).isNotNull();
        assertThat(userTaskRepository.findByInstanceIdAndTaskNodeIdAndStatus(
                instance.instanceId(), "ackTask", "OPEN")).isEmpty();
    }

    @Test
    void claimedOrphanTaskAlsoCloses() throws Exception {
        BpmnProcess process = workflowEngine.parse(BOUNDARY_TIMER_BPMN);
        WorkflowInstance instance = workflowEngine.start(
                "root.platform.workflows.boundary-wq-claimed",
                process
        );
        workflowEngine.runToCompletion(instance, process, (task, ignored) -> { }, expr -> true);
        instanceStore.save(instance, process, null, process.userTasks().get("ackTask"));

        WorkflowUserTaskEntity claimed = userTaskRepository
                .findByInstanceIdAndTaskNodeIdAndStatus(instance.instanceId(), "ackTask", "OPEN")
                .orElseThrow();
        claimed.setStatus("CLAIMED");
        claimed.setAssignee("operator-1");
        claimed.setClaimedAt(Instant.now());
        userTaskRepository.save(claimed);

        workflowEngine.fireDueTimers(
                instance,
                process,
                (task, ignored) -> { },
                (task, ignored) -> { },
                expr -> true
        );
        instanceStore.save(instance, process, null, null);

        assertThat(userTaskRepository.findById(claimed.getId()).orElseThrow().getStatus())
                .isEqualTo("COMPLETED");
    }
}
