package com.ispf.server.workflow;

import com.ispf.plugin.workflow.BpmnProcess;
import com.ispf.plugin.workflow.InstanceStatus;
import com.ispf.plugin.workflow.UserTaskDefinition;
import com.ispf.plugin.workflow.WorkflowConditionEvaluator;
import com.ispf.plugin.workflow.WorkflowEngine;
import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.plugin.workflow.WorkflowInstance;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.persistence.entity.WorkflowInstanceEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Advances a waiting workflow instance: user tasks, signals, messages, timers, and callActivity parents.
 * Transactions stay on {@link WorkflowService}; this type only holds the step sequence.
 */
final class WorkflowInstanceControl {

    private final WorkflowService workflows;
    private final ObjectManager objectManager;
    private final WorkflowEngine workflowEngine;
    private final WorkflowInstanceStore instanceStore;
    private final WorkflowTaskExecutor taskExecutor;
    private final WorkflowConditionFactory conditionFactory;
    private final WorkflowInstanceRepository instanceRepository;

    WorkflowInstanceControl(
            WorkflowService workflows,
            ObjectManager objectManager,
            WorkflowEngine workflowEngine,
            WorkflowInstanceStore instanceStore,
            WorkflowTaskExecutor taskExecutor,
            WorkflowConditionFactory conditionFactory,
            WorkflowInstanceRepository instanceRepository
    ) {
        this.workflows = workflows;
        this.objectManager = objectManager;
        this.workflowEngine = workflowEngine;
        this.instanceStore = instanceStore;
        this.taskExecutor = taskExecutor;
        this.conditionFactory = conditionFactory;
        this.instanceRepository = instanceRepository;
    }

    void claimInstance(String instanceId, String operatorId) throws WorkflowException {
        WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(instanceId);
        stored.instance().claim(operatorId);
        String bpmnXml = WorkflowService.readString(objectManager.require(stored.instance().workflowPath()), "bpmnXml")
                .orElseThrow(() -> new WorkflowException("BPMN missing"));
        BpmnProcess process = workflowEngine.parse(bpmnXml);
        UserTaskDefinition pendingTask = stored.instance().pendingUserTaskId()
                .map(id -> process.userTasks().get(id))
                .orElse(null);
        instanceStore.save(stored.instance(), process, stored.triggerObjectPath(), pendingTask);
    }

    WorkflowService.WorkflowView completeUserTask(String instanceId, String taskNodeId, String operatorId)
            throws WorkflowException {
        WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(instanceId);
        WorkflowInstance instance = stored.instance();
        if (instance.status() != InstanceStatus.WAITING) {
            throw new WorkflowException("Instance is not waiting: " + instanceId);
        }

        String workflowPath = instance.workflowPath();
        BpmnProcess process = workflows.parseProcess(workflowPath);

        String userTaskId = taskNodeId != null && !taskNodeId.isBlank()
                ? taskNodeId
                : instance.pendingUserTaskId()
                        .orElseThrow(() -> new WorkflowException("No pending user task"));
        UserTaskDefinition userTask = process.userTasks().get(userTaskId);
        if (userTask != null) {
            taskExecutor.executeUserTaskAction(userTask, stored.triggerObjectPath());
        }

        instance.claim(operatorId);
        WorkflowConditionEvaluator evaluator = conditionFactory.forTriggerObjectPath(stored.triggerObjectPath());
        workflowEngine.completeUserTask(
                instance,
                process,
                userTaskId,
                taskExecutor::executeServiceTask,
                taskExecutor::executeMessageTask,
                evaluator
        );
        workflows.finishStep(stored.triggerObjectPath(), instance, process, instance.variables());
        return workflows.getWorkflow(workflowPath);
    }

    Map<String, Object> deliverSignal(String instanceId, String signalName, String operatorId)
            throws WorkflowException {
        if (signalName == null || signalName.isBlank()) {
            throw new WorkflowException("Signal name is required");
        }
        WorkflowInstance instance = resumeWaitingInstance(
                instanceId,
                operatorId,
                waiting -> {
                    if (!waiting.pendingSignalNames().contains(signalName)) {
                        throw new WorkflowException("Instance is not waiting for signal: " + signalName);
                    }
                },
                (waiting, process, evaluator) -> workflowEngine.deliverSignal(
                        waiting,
                        process,
                        signalName,
                        taskExecutor::executeServiceTask,
                        taskExecutor::executeMessageTask,
                        evaluator
                )
        );
        return Map.of(
                "instanceId", instanceId,
                "signal", signalName,
                "status", instance.status().name()
        );
    }

    Map<String, Object> deliverMessage(String instanceId, String messageName, String operatorId)
            throws WorkflowException {
        if (messageName == null || messageName.isBlank()) {
            throw new WorkflowException("Message name is required");
        }
        WorkflowInstance instance = resumeWaitingInstance(
                instanceId,
                operatorId,
                waiting -> {
                    if (!waiting.pendingMessageNames().contains(messageName)) {
                        throw new WorkflowException("Instance is not waiting for message: " + messageName);
                    }
                },
                (waiting, process, evaluator) -> workflowEngine.deliverMessage(
                        waiting,
                        process,
                        messageName,
                        taskExecutor::executeServiceTask,
                        taskExecutor::executeMessageTask,
                        evaluator
                )
        );
        return Map.of(
                "instanceId", instanceId,
                "message", messageName,
                "status", instance.status().name()
        );
    }

    Map<String, Object> fireDueTimers(String instanceId, String operatorId) throws WorkflowException {
        WorkflowInstance instance = resumeWaitingInstance(
                instanceId,
                operatorId,
                waiting -> {
                    if (!waiting.hasDueTimers(System.currentTimeMillis())) {
                        throw new WorkflowException("No due timers for instance: " + instanceId);
                    }
                },
                (waiting, process, evaluator) -> workflowEngine.fireDueTimers(
                        waiting,
                        process,
                        taskExecutor::executeServiceTask,
                        taskExecutor::executeMessageTask,
                        evaluator
                )
        );
        return Map.of(
                "instanceId", instanceId,
                "status", instance.status().name()
        );
    }

    Map<String, Object> deliverSignalByWorkflowPath(String workflowPath, String signalName, String operatorId)
            throws WorkflowException {
        if (signalName == null || signalName.isBlank()) {
            throw new WorkflowException("Signal name is required");
        }
        List<WorkflowInstanceEntity> waiting = instanceRepository.findByWorkflowPathAndStatus(
                workflowPath,
                InstanceStatus.WAITING.name()
        );
        List<String> signaled = new ArrayList<>();
        for (WorkflowInstanceEntity entity : waiting) {
            WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(entity.getId());
            if (!stored.instance().pendingSignalNames().contains(signalName)) {
                continue;
            }
            deliverSignal(entity.getId(), signalName, operatorId);
            signaled.add(entity.getId());
        }
        return Map.of(
                "workflowPath", workflowPath,
                "signal", signalName,
                "signaledCount", signaled.size(),
                "instanceIds", signaled
        );
    }

    Map<String, Object> deliverMessageByWorkflowPath(String workflowPath, String messageName, String operatorId)
            throws WorkflowException {
        if (messageName == null || messageName.isBlank()) {
            throw new WorkflowException("Message name is required");
        }
        List<WorkflowInstanceEntity> waiting = instanceRepository.findByWorkflowPathAndStatus(
                workflowPath,
                InstanceStatus.WAITING.name()
        );
        List<String> delivered = new ArrayList<>();
        for (WorkflowInstanceEntity entity : waiting) {
            WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(entity.getId());
            if (!stored.instance().pendingMessageNames().contains(messageName)) {
                continue;
            }
            deliverMessage(entity.getId(), messageName, operatorId);
            delivered.add(entity.getId());
        }
        return Map.of(
                "workflowPath", workflowPath,
                "message", messageName,
                "deliveredCount", delivered.size(),
                "instanceIds", delivered
        );
    }

    void resumeParentCallActivity(
            String parentInstanceId,
            String childInstanceId,
            String childFailedMessage,
            Map<String, String> childVariables
    ) throws WorkflowException {
        WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(parentInstanceId);
        WorkflowInstance parent = stored.instance();
        if (parent.status() != InstanceStatus.WAITING) {
            return;
        }
        boolean waitingOnChild = parent.waitingTokens().stream()
                .anyMatch(token -> childInstanceId.equals(token.pendingCallChildInstanceId()));
        if (!waitingOnChild) {
            return;
        }

        BpmnProcess process = workflows.parseProcess(parent.workflowPath());
        WorkflowConditionEvaluator evaluator = conditionFactory.forTriggerObjectPath(stored.triggerObjectPath());
        workflowEngine.resumeAfterCallActivityChild(
                parent,
                process,
                childInstanceId,
                childVariables,
                childFailedMessage,
                taskExecutor::executeServiceTask,
                taskExecutor::executeMessageTask,
                evaluator
        );
        workflows.finishStep(stored.triggerObjectPath(), parent, process, parent.variables());
    }

    private WorkflowInstance resumeWaitingInstance(
            String instanceId,
            String operatorId,
            WaitingInstanceGuard guard,
            EngineStep step
    ) throws WorkflowException {
        WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(instanceId);
        WorkflowInstance instance = stored.instance();
        if (instance.status() != InstanceStatus.WAITING) {
            throw new WorkflowException("Instance is not waiting: " + instanceId);
        }
        guard.check(instance);

        BpmnProcess process = workflows.parseProcess(instance.workflowPath());
        if (operatorId != null && !operatorId.isBlank()) {
            instance.claim(operatorId);
        }
        step.run(instance, process, conditionFactory.forTriggerObjectPath(stored.triggerObjectPath()));
        workflows.finishStep(stored.triggerObjectPath(), instance, process, instance.variables());
        return instance;
    }

    @FunctionalInterface
    private interface WaitingInstanceGuard {
        void check(WorkflowInstance instance) throws WorkflowException;
    }

    @FunctionalInterface
    private interface EngineStep {
        void run(WorkflowInstance instance, BpmnProcess process, WorkflowConditionEvaluator evaluator)
                throws WorkflowException;
    }
}
