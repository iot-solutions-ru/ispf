package com.ispf.plugin.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WorkflowEngine {

    private final BpmnParser parser = new BpmnParser();
    private CallActivityExecutor callActivityExecutor = (call, parent) -> {
        throw new WorkflowException("callActivity executor is not configured");
    };

    public void setCallActivityExecutor(CallActivityExecutor callActivityExecutor) {
        this.callActivityExecutor = callActivityExecutor != null
                ? callActivityExecutor
                : (call, parent) -> {
                    throw new WorkflowException("callActivity executor is not configured");
                };
    }

    public BpmnProcess parse(String bpmnXml) throws WorkflowException {
        return parser.parse(bpmnXml);
    }

    public WorkflowInstance start(String workflowPath, BpmnProcess process) {
        return new WorkflowInstance(UUID.randomUUID().toString(), workflowPath, process.startNodeId());
    }

    public WorkflowInstance runToCompletion(
            WorkflowInstance instance,
            BpmnProcess process,
            WorkflowActionExecutor executor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        return runToCompletion(instance, process, executor, (task, ignored) -> { }, evaluator);
    }

    public WorkflowInstance runToCompletion(
            WorkflowInstance instance,
            BpmnProcess process,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        while (instance.status() == InstanceStatus.RUNNING) {
            step(instance, process, executor, messageExecutor, evaluator);
        }
        return instance;
    }

    public void step(
            WorkflowInstance instance,
            BpmnProcess process,
            WorkflowActionExecutor executor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        step(instance, process, executor, (task, ignored) -> { }, evaluator);
    }

    public void step(
            WorkflowInstance instance,
            BpmnProcess process,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        if (instance.status() != InstanceStatus.RUNNING) {
            return;
        }

        List<ExecutionToken> activeTokens = new ArrayList<>(instance.activeTokens());
        for (ExecutionToken token : activeTokens) {
            if (instance.status() != InstanceStatus.RUNNING) {
                break;
            }
            stepToken(instance, process, token, executor, messageExecutor, evaluator);
        }
        instance.recomputeStatus();
    }

    public void completeUserTask(
            WorkflowInstance instance,
            BpmnProcess process,
            WorkflowActionExecutor executor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        completeUserTask(instance, process, instance.pendingUserTaskId().orElseThrow(), executor, evaluator);
    }

    public void completeUserTask(
            WorkflowInstance instance,
            BpmnProcess process,
            String userTaskNodeId,
            WorkflowActionExecutor executor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        completeUserTask(instance, process, userTaskNodeId, executor, (task, ignored) -> { }, evaluator);
    }

    public void completeUserTask(
            WorkflowInstance instance,
            BpmnProcess process,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        completeUserTask(
                instance,
                process,
                instance.pendingUserTaskId().orElseThrow(),
                executor,
                messageExecutor,
                evaluator
        );
    }

    public void completeUserTask(
            WorkflowInstance instance,
            BpmnProcess process,
            String userTaskNodeId,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        if (instance.status() != InstanceStatus.WAITING) {
            throw new WorkflowException("Instance is not waiting for user task");
        }
        ExecutionToken token = findWaitingToken(instance, userTaskNodeId)
                .orElseThrow(() -> new WorkflowException("No token for user task: " + userTaskNodeId));
        instance.resumeUserTask(userTaskNodeId);
        advanceToken(instance, process, token, userTaskNodeId, executor, messageExecutor, evaluator);

        while (instance.status() == InstanceStatus.RUNNING) {
            step(instance, process, executor, messageExecutor, evaluator);
        }
    }

    /**
     * Resume parent tokens waiting on a child callActivity instance.
     * When {@code childFailedMessage} is non-null, the parent fails instead of advancing.
     */
    public void resumeAfterCallActivityChild(
            WorkflowInstance instance,
            BpmnProcess process,
            String childInstanceId,
            Map<String, String> childVariables,
            String childFailedMessage,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        if (childFailedMessage != null) {
            instance.fail("callActivity child failed: " + childFailedMessage);
            return;
        }
        List<ExecutionToken> waiting = instance.waitingTokens().stream()
                .filter(token -> childInstanceId.equals(token.pendingCallChildInstanceId()))
                .toList();
        if (waiting.isEmpty()) {
            throw new WorkflowException("No token waiting for callActivity child: " + childInstanceId);
        }
        if (!instance.resumeCallActivityChild(childInstanceId)) {
            throw new WorkflowException("Failed to resume callActivity child: " + childInstanceId);
        }
        for (ExecutionToken token : waiting) {
            String callNodeId = token.currentNodeId();
            if (childVariables != null) {
                childVariables.forEach((key, value) -> {
                    if (key != null && !key.startsWith("__")) {
                        instance.setVariable(
                                "call." + callNodeId + "." + key,
                                value == null ? "" : value
                        );
                    }
                });
            }
            advanceToken(instance, process, token, callNodeId, executor, messageExecutor, evaluator);
        }
        while (instance.status() == InstanceStatus.RUNNING) {
            step(instance, process, executor, messageExecutor, evaluator);
        }
    }

    public void deliverSignal(
            WorkflowInstance instance,
            BpmnProcess process,
            String signalName,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        if (instance.status() != InstanceStatus.WAITING) {
            throw new WorkflowException("Instance is not waiting for signal");
        }
        List<ExecutionToken> resumed = instance.resumeSignal(signalName);
        for (ExecutionToken token : resumed) {
            String catchNodeId = token.currentNodeId();
            advanceToken(instance, process, token, catchNodeId, executor, messageExecutor, evaluator);
        }
        while (instance.status() == InstanceStatus.RUNNING) {
            step(instance, process, executor, messageExecutor, evaluator);
        }
    }

    public void deliverMessage(
            WorkflowInstance instance,
            BpmnProcess process,
            String messageName,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        if (instance.status() != InstanceStatus.WAITING) {
            throw new WorkflowException("Instance is not waiting for message");
        }
        List<ExecutionToken> resumed;
        try {
            resumed = instance.resumeMessage(messageName);
        } catch (IllegalStateException e) {
            throw new WorkflowException(
                    "No waiting token for message: " + messageName
                            + " (pending: " + instance.pendingMessageNames() + ")",
                    e);
        }
        for (ExecutionToken token : resumed) {
            String catchNodeId = token.currentNodeId();
            advanceToken(instance, process, token, catchNodeId, executor, messageExecutor, evaluator);
        }
        while (instance.status() == InstanceStatus.RUNNING) {
            step(instance, process, executor, messageExecutor, evaluator);
        }
    }

    public void fireDueTimers(
            WorkflowInstance instance,
            BpmnProcess process,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        fireDueTimers(instance, process, executor, messageExecutor, evaluator, System.currentTimeMillis());
    }

    public void fireDueTimers(
            WorkflowInstance instance,
            BpmnProcess process,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator,
            long nowEpochMs
    ) throws WorkflowException {
        if (instance.status() != InstanceStatus.WAITING) {
            throw new WorkflowException("Instance is not waiting for timer");
        }
        if (!instance.hasDueTimers(nowEpochMs)) {
            throw new WorkflowException("No due timers for instance");
        }

        List<ExecutionToken> dueTokens = new ArrayList<>(instance.dueTimerTokens(nowEpochMs));
        for (ExecutionToken token : dueTokens) {
            if (token.pendingBoundaryTimerNodeId() != null) {
                String boundaryNodeId = token.pendingBoundaryTimerNodeId();
                BoundaryTimerDefinition boundary = findBoundaryTimer(process, boundaryNodeId);
                if (boundary == null) {
                    instance.fail("Boundary timer definition missing: " + boundaryNodeId);
                    return;
                }
                ExecutionToken resumed = instance.resumeBoundaryTimer(boundaryNodeId);
                advanceToken(instance, process, resumed, boundaryNodeId, executor, messageExecutor, evaluator);
            } else if (token.pendingTimerCatchNodeId() != null) {
                String catchNodeId = token.pendingTimerCatchNodeId();
                List<ExecutionToken> resumed = instance.resumeTimerCatch(catchNodeId);
                for (ExecutionToken resumedToken : resumed) {
                    advanceToken(instance, process, resumedToken, catchNodeId, executor, messageExecutor, evaluator);
                }
            }
        }

        while (instance.status() == InstanceStatus.RUNNING) {
            step(instance, process, executor, messageExecutor, evaluator);
        }
    }

    private static BoundaryTimerDefinition findBoundaryTimer(BpmnProcess process, String boundaryNodeId) {
        for (BoundaryTimerDefinition boundary : process.boundaryTimers().values()) {
            if (boundaryNodeId.equals(boundary.id())) {
                return boundary;
            }
        }
        return null;
    }

    private static long deadlineFromNow(int durationSeconds) {
        return System.currentTimeMillis() + Math.max(0, durationSeconds) * 1000L;
    }

    private void stepToken(
            WorkflowInstance instance,
            BpmnProcess process,
            ExecutionToken token,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        if (token.state() != TokenState.ACTIVE) {
            return;
        }

        String nodeId = token.currentNodeId();
        String nodeType = process.nodeTypes().get(nodeId);
        if (nodeType == null) {
            instance.fail("Unknown node: " + nodeId);
            return;
        }

        switch (nodeType) {
            case "startEvent" -> advanceToken(instance, process, token, nodeId, executor, messageExecutor, evaluator);
            case "exclusiveGateway" -> advanceToken(instance, process, token, nodeId, executor, messageExecutor, evaluator);
            case "parallelGateway" -> handleParallelGateway(instance, process, token, nodeId, executor, messageExecutor, evaluator);
            case "serviceTask" -> {
                ServiceTaskDefinition task = process.serviceTasks().get(nodeId);
                if (task == null) {
                    instance.fail("Service task definition missing: " + nodeId);
                    return;
                }
                Map<String, Object> stepInput = new java.util.LinkedHashMap<>();
                stepInput.put("action", task.action().name());
                stepInput.put("name", task.name() == null ? "" : task.name());
                stepInput.put("parameters", redactAiParameters(task));
                WorkflowStepRecord started = WorkflowStepRecord.started(
                        token.tokenId(),
                        instance.nextStepSeq(),
                        nodeId,
                        nodeType,
                        stepInput,
                        1
                );
                try {
                    executor.execute(task, instance);
                    instance.addPendingStep(started.completed(Map.of("status", "OK")));
                } catch (WorkflowException e) {
                    instance.addPendingStep(started.failed(e.getMessage()));
                    instance.fail(e.getMessage());
                    return;
                }
                advanceToken(instance, process, token, nodeId, executor, messageExecutor, evaluator);
            }
            case "messageTask" -> {
                MessageTaskDefinition task = process.messageTasks().get(nodeId);
                if (task == null) {
                    instance.fail("Message task definition missing: " + nodeId);
                    return;
                }
                WorkflowStepRecord started = WorkflowStepRecord.started(
                        token.tokenId(),
                        instance.nextStepSeq(),
                        nodeId,
                        nodeType,
                        Map.of(
                                "subject", task.subject() == null ? "" : task.subject(),
                                "name", task.name() == null ? "" : task.name()
                        ),
                        1
                );
                try {
                    messageExecutor.execute(task, instance);
                    instance.addPendingStep(started.completed(Map.of("status", "OK")));
                } catch (WorkflowException e) {
                    instance.addPendingStep(started.failed(e.getMessage()));
                    instance.fail(e.getMessage());
                    return;
                }
                advanceToken(instance, process, token, nodeId, executor, messageExecutor, evaluator);
            }
            case "userTask" -> {
                if (process.userTasks().get(nodeId) == null) {
                    instance.fail("User task definition missing: " + nodeId);
                    return;
                }
                instance.waitTokenAt(token, nodeId);
                BoundaryTimerDefinition boundary = process.boundaryTimers().get(nodeId);
                if (boundary != null) {
                    instance.scheduleBoundaryTimer(token, boundary.id(), deadlineFromNow(boundary.durationSeconds()));
                }
            }
            case "intermediateThrowEvent" -> {
                MessageThrowDefinition throwDef = process.messageThrowEvents().get(nodeId);
                if (throwDef == null) {
                    instance.fail("Message throw definition missing: " + nodeId);
                    return;
                }
                try {
                    messageExecutor.execute(
                            new MessageTaskDefinition(
                                    throwDef.id(),
                                    throwDef.name(),
                                    throwDef.messageName(),
                                    throwDef.messageName(),
                                    "bpmn-throw",
                                    throwDef.parameters()
                            ),
                            instance
                    );
                } catch (WorkflowException e) {
                    instance.fail(e.getMessage());
                    return;
                }
                advanceToken(instance, process, token, nodeId, executor, messageExecutor, evaluator);
            }
            case "callActivity" -> {
                CallActivityDefinition call = process.callActivities().get(nodeId);
                if (call == null) {
                    instance.fail("callActivity definition missing: " + nodeId);
                    return;
                }
                WorkflowStepRecord started = WorkflowStepRecord.started(
                        token.tokenId(),
                        instance.nextStepSeq(),
                        nodeId,
                        nodeType,
                        Map.of(
                                "workflowPath", call.workflowPath() == null ? "" : call.workflowPath(),
                                "name", call.name() == null ? "" : call.name()
                        ),
                        1
                );
                try {
                    CallActivityExecutor.Result result = callActivityExecutor.execute(call, instance);
                    if (result.status() == InstanceStatus.FAILED) {
                        String err = result.errorMessage() == null ? "child workflow failed" : result.errorMessage();
                        instance.addPendingStep(started.failed(err));
                        instance.fail("callActivity failed: " + err);
                        return;
                    }
                    if (result.status() == InstanceStatus.WAITING) {
                        instance.addPendingStep(started.completed(Map.of(
                                "status", "WAITING",
                                "childInstanceId", result.childInstanceId() == null ? "" : result.childInstanceId()
                        )));
                        instance.waitTokenAtCallActivity(token, nodeId, result.childInstanceId());
                        return;
                    }
                    if (result.variables() != null) {
                        result.variables().forEach((key, value) -> {
                            if (key != null && !key.startsWith("__")) {
                                instance.setVariable("call." + call.id() + "." + key, value == null ? "" : value);
                            }
                        });
                    }
                    instance.addPendingStep(started.completed(Map.of(
                            "status", "COMPLETED",
                            "childInstanceId", result.childInstanceId() == null ? "" : result.childInstanceId()
                    )));
                } catch (WorkflowException e) {
                    instance.addPendingStep(started.failed(e.getMessage()));
                    instance.fail(e.getMessage());
                    return;
                }
                advanceToken(instance, process, token, nodeId, executor, messageExecutor, evaluator);
            }
            case "subProcess" -> enterSubProcess(instance, process, token, nodeId, executor, messageExecutor, evaluator);
            case "intermediateCatchEvent" -> {
                TimerCatchDefinition timerCatch = process.timerCatchEvents().get(nodeId);
                if (timerCatch != null) {
                    instance.waitTokenAtTimer(token, nodeId, deadlineFromNow(timerCatch.durationSeconds()));
                    return;
                }
                MessageCatchDefinition messageCatch = process.messageCatchEvents().get(nodeId);
                if (messageCatch != null) {
                    instance.waitTokenAtMessage(token, nodeId, messageCatch.messageName());
                    return;
                }
                SignalCatchDefinition catchDef = process.signalCatchEvents().get(nodeId);
                if (catchDef == null) {
                    instance.fail("Catch event definition missing: " + nodeId);
                    return;
                }
                instance.waitTokenAtSignal(token, nodeId, catchDef.signalName());
            }
            case "endEvent" -> {
                if (process.isSubProcessEnd(nodeId)) {
                    exitSubProcess(instance, process, token, process.subProcessForEndEvent(nodeId),
                            executor, messageExecutor, evaluator);
                    return;
                }
                token.complete();
                instance.removeToken(token);
                if (instance.tokens().isEmpty()) {
                    instance.complete();
                } else {
                    instance.recomputeStatus();
                }
            }
            default -> instance.fail("Unsupported node type: " + nodeType);
        }
    }

    private void handleParallelGateway(
            WorkflowInstance instance,
            BpmnProcess process,
            ExecutionToken token,
            String nodeId,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        if (process.isParallelFork(nodeId)) {
            forkParallel(instance, process, token, nodeId, executor, messageExecutor, evaluator);
            return;
        }
        if (process.isParallelJoin(nodeId)) {
            handleParallelJoin(instance, process, token, nodeId, executor, messageExecutor, evaluator);
            return;
        }
        advanceToken(instance, process, token, nodeId, executor, messageExecutor, evaluator);
    }

    private void forkParallel(
            WorkflowInstance instance,
            BpmnProcess process,
            ExecutionToken token,
            String forkNodeId,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        List<SequenceFlowDefinition> outgoing = process.outgoingFrom(forkNodeId);
        String joinNodeId = findJoinNode(process, forkNodeId);
        if (joinNodeId == null && outgoing.size() > 1) {
            instance.fail("Parallel fork without join is not supported: " + forkNodeId);
            return;
        }

        instance.moveToken(token, outgoing.getFirst().targetRef());
        List<ExecutionToken> branchTokens = new ArrayList<>();
        branchTokens.add(token);
        for (int i = 1; i < outgoing.size(); i++) {
            ExecutionToken branchToken = instance.createToken(outgoing.get(i).targetRef());
            instance.moveToken(branchToken, outgoing.get(i).targetRef());
            branchTokens.add(branchToken);
        }
        for (ExecutionToken branchToken : branchTokens) {
            runBranchUntilBlocked(instance, process, branchToken, executor, messageExecutor, evaluator);
        }
    }

    private void runBranchUntilBlocked(
            WorkflowInstance instance,
            BpmnProcess process,
            ExecutionToken token,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        while (token.state() == TokenState.ACTIVE && instance.status() == InstanceStatus.RUNNING) {
            stepToken(instance, process, token, executor, messageExecutor, evaluator);
        }
    }

    private void handleParallelJoin(
            WorkflowInstance instance,
            BpmnProcess process,
            ExecutionToken token,
            String joinNodeId,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        token.arriveAtJoin(joinNodeId);
        int expected = process.incomingTo(joinNodeId).size();
        if (instance.tokensAtJoin(joinNodeId).size() < expected) {
            instance.recomputeStatus();
            return;
        }

        ExecutionToken merged = instance.mergeTokensAtJoin(joinNodeId);
        advanceToken(instance, process, merged, joinNodeId, executor, messageExecutor, evaluator);
    }

    private void advanceToken(
            WorkflowInstance instance,
            BpmnProcess process,
            ExecutionToken token,
            String nodeId,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        if (token.state() != TokenState.ACTIVE) {
            return;
        }

        String next = process.resolveNext(nodeId, evaluator);
        if (next == null) {
            if (process.isEnd(nodeId)) {
                token.complete();
                instance.removeToken(token);
                if (instance.tokens().isEmpty()) {
                    instance.complete();
                }
            } else {
                instance.fail("No outgoing flow from node: " + nodeId);
            }
            return;
        }

        instance.moveToken(token, next);
        if (process.isSubProcess(next)) {
            enterSubProcess(instance, process, token, next, executor, messageExecutor, evaluator);
            return;
        }
        if (process.isEnd(next)) {
            if (process.isSubProcessEnd(next)) {
                exitSubProcess(instance, process, token, process.subProcessForEndEvent(next),
                        executor, messageExecutor, evaluator);
                return;
            }
            token.complete();
            instance.removeToken(token);
            if (instance.tokens().isEmpty()) {
                instance.complete();
            }
            return;
        }

        stepToken(instance, process, token, executor, messageExecutor, evaluator);
    }

    private static String findJoinNode(BpmnProcess process, String forkNodeId) {
        List<SequenceFlowDefinition> outgoing = process.outgoingFrom(forkNodeId);
        if (outgoing.isEmpty()) {
            return null;
        }
        String candidate = null;
        for (SequenceFlowDefinition flow : outgoing) {
            String join = findJoinFromBranch(process, flow.targetRef());
            if (join == null) {
                return null;
            }
            if (candidate == null) {
                candidate = join;
            } else if (!candidate.equals(join)) {
                return null;
            }
        }
        return candidate;
    }

    private static String findJoinFromBranch(BpmnProcess process, String nodeId) {
        if (process.isParallelJoin(nodeId)) {
            return nodeId;
        }
        if (process.isEnd(nodeId)) {
            return null;
        }
        List<SequenceFlowDefinition> outgoing = process.outgoingFrom(nodeId);
        if (outgoing.size() != 1) {
            return null;
        }
        return findJoinFromBranch(process, outgoing.getFirst().targetRef());
    }

    private void enterSubProcess(
            WorkflowInstance instance,
            BpmnProcess process,
            ExecutionToken token,
            String subProcessNodeId,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        SubProcessDefinition subProcess = process.subProcesses().get(subProcessNodeId);
        if (subProcess == null) {
            instance.fail("Subprocess definition missing: " + subProcessNodeId);
            return;
        }
        instance.moveToken(token, subProcess.startNodeId());
        stepToken(instance, process, token, executor, messageExecutor, evaluator);
    }

    private void exitSubProcess(
            WorkflowInstance instance,
            BpmnProcess process,
            ExecutionToken token,
            String subProcessNodeId,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        SubProcessDefinition subProcess = process.subProcesses().get(subProcessNodeId);
        if (subProcess == null) {
            instance.fail("Subprocess definition missing: " + subProcessNodeId);
            return;
        }
        List<SequenceFlowDefinition> outgoing = process.outgoingFrom(subProcessNodeId);
        if (outgoing.isEmpty()) {
            instance.fail("Subprocess has no outgoing flow: " + subProcessNodeId);
            return;
        }
        instance.moveToken(token, outgoing.getFirst().targetRef());
        stepToken(instance, process, token, executor, messageExecutor, evaluator);
    }

    private static java.util.Optional<ExecutionToken> findWaitingToken(
            WorkflowInstance instance,
            String userTaskNodeId
    ) {
        return instance.waitingTokens().stream()
                .filter(token -> userTaskNodeId.equals(token.pendingUserTaskId()))
                .findFirst();
    }

    /**
     * ADR-0049: do not persist full LLM prompts in the execution journal.
     */
    private static Map<String, String> redactAiParameters(ServiceTaskDefinition task) {
        Map<String, String> params = new java.util.LinkedHashMap<>(task.parameters());
        if (task.action() != WorkflowActionType.LLM_COMPLETE && task.action() != WorkflowActionType.INVOKE_AGENT) {
            return params;
        }
        String prompt = params.getOrDefault("promptTemplate", params.getOrDefault("goalTemplate", ""));
        params.remove("promptTemplate");
        params.remove("goalTemplate");
        params.put("promptHash", Integer.toHexString(prompt.hashCode()));
        params.put("promptChars", String.valueOf(prompt.length()));
        return params;
    }
}
