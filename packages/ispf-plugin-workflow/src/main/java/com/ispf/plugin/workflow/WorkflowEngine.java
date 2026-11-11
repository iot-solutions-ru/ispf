package com.ispf.plugin.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WorkflowEngine {

    static final int MAX_SYNC_STEPS = 1000;

    private static final ThreadLocal<int[]> SYNC_STEP_COUNT = new ThreadLocal<>();

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
        drain(instance, process, executor, messageExecutor, evaluator);
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
        if (activeTokens.isEmpty()) {
            instance.fail("Workflow instance is RUNNING without active tokens");
            return;
        }
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
        withSyncBudget(() -> {
            advanceToken(instance, process, token, userTaskNodeId, executor, messageExecutor, evaluator);
            instance.recomputeStatus();
            drain(instance, process, executor, messageExecutor, evaluator);
        });
    }

    /**
     * Resume parent tokens waiting on a child callActivity instance.
     * A non-null {@code childFailedMessage} follows an error boundary on the call activity when one matches;
     * otherwise the parent fails.
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
        List<ExecutionToken> waiting = instance.waitingTokens().stream()
                .filter(token -> childInstanceId.equals(token.pendingCallChildInstanceId()))
                .toList();
        if (childFailedMessage != null) {
            if (waiting.isEmpty() || !instance.resumeCallActivityChild(childInstanceId)) {
                instance.fail("callActivity child failed: " + childFailedMessage);
                return;
            }
            WorkflowException failure = new WorkflowException("callActivity child failed: " + childFailedMessage);
            withSyncBudget(() -> {
                for (ExecutionToken token : waiting) {
                    BoundaryErrorDefinition boundary = takeErrorBoundary(instance, process, token, failure);
                    if (boundary == null) {
                        instance.fail(failure.getMessage());
                        return;
                    }
                    advanceToken(instance, process, token, boundary.id(), executor, messageExecutor, evaluator);
                }
                instance.recomputeStatus();
                drain(instance, process, executor, messageExecutor, evaluator);
            });
            return;
        }
        if (waiting.isEmpty()) {
            throw new WorkflowException("No token waiting for callActivity child: " + childInstanceId);
        }
        if (!instance.resumeCallActivityChild(childInstanceId)) {
            throw new WorkflowException("Failed to resume callActivity child: " + childInstanceId);
        }
        withSyncBudget(() -> {
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
            instance.recomputeStatus();
            drain(instance, process, executor, messageExecutor, evaluator);
        });
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
        withSyncBudget(() -> {
            for (ExecutionToken token : resumed) {
                String catchNodeId = token.currentNodeId();
                advanceToken(instance, process, token, catchNodeId, executor, messageExecutor, evaluator);
            }
            instance.recomputeStatus();
            drain(instance, process, executor, messageExecutor, evaluator);
        });
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
        withSyncBudget(() -> {
            for (ExecutionToken token : resumed) {
                String catchNodeId = token.currentNodeId();
                advanceToken(instance, process, token, catchNodeId, executor, messageExecutor, evaluator);
            }
            instance.recomputeStatus();
            drain(instance, process, executor, messageExecutor, evaluator);
        });
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
        withSyncBudget(() -> {
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
            instance.recomputeStatus();
            drain(instance, process, executor, messageExecutor, evaluator);
        });
    }

    /**
     * Records a matching error boundary and returns it. The caller moves the token along that
     * boundary's outgoing flow. A failed activity does not keep a second token, so
     * {@code cancelActivity} is not applied here.
     */
    private BoundaryErrorDefinition takeErrorBoundary(
            WorkflowInstance instance,
            BpmnProcess process,
            ExecutionToken token,
            WorkflowException error
    ) {
        List<BoundaryErrorDefinition> boundaries = process.boundaryErrors()
                .getOrDefault(token.currentNodeId(), List.of());
        BoundaryErrorDefinition boundary = BoundaryErrorDefinition.match(boundaries, error.getMessage());
        if (boundary == null) {
            return null;
        }
        instance.setVariable("errorMessage", error.getMessage() == null ? "" : error.getMessage());
        instance.setVariable("errorCode", boundary.errorCode() == null ? "" : boundary.errorCode());
        return boundary;
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
        if (SYNC_STEP_COUNT.get() == null) {
            withSyncBudget(() -> stepToken(instance, process, token, executor, messageExecutor, evaluator));
            return;
        }
        while (token.state() == TokenState.ACTIVE && instance.status() == InstanceStatus.RUNNING) {
            if (!consumeSyncStep(instance)) {
                return;
            }
            if (!executeSingleTokenNode(instance, process, token, executor, messageExecutor, evaluator)) {
                return;
            }
        }
    }

    /**
     * Executes the current node for {@code token}. Returns {@code true} when {@code token} was
     * synchronously moved to a successor node and should continue stepping in the caller's loop;
     * returns {@code false} when {@code token} completed, blocked, joined, or failed.
     */
    private boolean executeSingleTokenNode(
            WorkflowInstance instance,
            BpmnProcess process,
            ExecutionToken token,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        String nodeId = token.currentNodeId();
        String nodeType = process.nodeTypes().get(nodeId);
        if (nodeType == null) {
            instance.fail("Unknown node: " + nodeId);
            return false;
        }

        switch (nodeType) {
            case "startEvent" -> {
                return advanceTokenToNext(instance, process, token, nodeId, evaluator);
            }
            case "exclusiveGateway" -> {
                return advanceTokenToNext(instance, process, token, nodeId, evaluator);
            }
            case "parallelGateway" -> {
                return handleParallelGateway(instance, process, token, nodeId, executor, messageExecutor, evaluator);
            }
            case "serviceTask" -> {
                ServiceTaskDefinition task = process.serviceTasks().get(nodeId);
                if (task == null) {
                    instance.fail("Service task definition missing: " + nodeId);
                    return false;
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
                    BoundaryErrorDefinition boundary = takeErrorBoundary(instance, process, token, e);
                    if (boundary != null) {
                        return advanceTokenToNext(instance, process, token, boundary.id(), evaluator);
                    }
                    instance.fail(e.getMessage());
                    return false;
                }
                return advanceTokenToNext(instance, process, token, nodeId, evaluator);
            }
            case "messageTask" -> {
                MessageTaskDefinition task = process.messageTasks().get(nodeId);
                if (task == null) {
                    instance.fail("Message task definition missing: " + nodeId);
                    return false;
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
                    BoundaryErrorDefinition boundary = takeErrorBoundary(instance, process, token, e);
                    if (boundary != null) {
                        return advanceTokenToNext(instance, process, token, boundary.id(), evaluator);
                    }
                    instance.fail(e.getMessage());
                    return false;
                }
                return advanceTokenToNext(instance, process, token, nodeId, evaluator);
            }
            case "userTask" -> {
                if (process.userTasks().get(nodeId) == null) {
                    instance.fail("User task definition missing: " + nodeId);
                    return false;
                }
                instance.waitTokenAt(token, nodeId);
                BoundaryTimerDefinition boundary = process.boundaryTimers().get(nodeId);
                if (boundary != null) {
                    instance.scheduleBoundaryTimer(token, boundary.id(), deadlineFromNow(boundary.durationSeconds()));
                }
                return false;
            }
            case "intermediateThrowEvent" -> {
                MessageThrowDefinition throwDef = process.messageThrowEvents().get(nodeId);
                if (throwDef == null) {
                    instance.fail("Message throw definition missing: " + nodeId);
                    return false;
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
                    return false;
                }
                return advanceTokenToNext(instance, process, token, nodeId, evaluator);
            }
            case "callActivity" -> {
                CallActivityDefinition call = process.callActivities().get(nodeId);
                if (call == null) {
                    instance.fail("callActivity definition missing: " + nodeId);
                    return false;
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
                        WorkflowException failure = new WorkflowException("callActivity failed: " + err);
                        BoundaryErrorDefinition boundary = takeErrorBoundary(instance, process, token, failure);
                        if (boundary != null) {
                            return advanceTokenToNext(instance, process, token, boundary.id(), evaluator);
                        }
                        instance.fail(failure.getMessage());
                        return false;
                    }
                    if (result.status() == InstanceStatus.WAITING) {
                        instance.addPendingStep(started.completed(Map.of(
                                "status", "WAITING",
                                "childInstanceId", result.childInstanceId() == null ? "" : result.childInstanceId()
                        )));
                        instance.waitTokenAtCallActivity(token, nodeId, result.childInstanceId());
                        return false;
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
                    BoundaryErrorDefinition boundary = takeErrorBoundary(instance, process, token, e);
                    if (boundary != null) {
                        return advanceTokenToNext(instance, process, token, boundary.id(), evaluator);
                    }
                    instance.fail(e.getMessage());
                    return false;
                }
                return advanceTokenToNext(instance, process, token, nodeId, evaluator);
            }
            case "subProcess" -> {
                return enterSubProcessToNext(instance, process, token, nodeId);
            }
            case "intermediateCatchEvent" -> {
                TimerCatchDefinition timerCatch = process.timerCatchEvents().get(nodeId);
                if (timerCatch != null) {
                    instance.waitTokenAtTimer(token, nodeId, deadlineFromNow(timerCatch.durationSeconds()));
                    return false;
                }
                MessageCatchDefinition messageCatch = process.messageCatchEvents().get(nodeId);
                if (messageCatch != null) {
                    instance.waitTokenAtMessage(token, nodeId, messageCatch.messageName());
                    return false;
                }
                SignalCatchDefinition catchDef = process.signalCatchEvents().get(nodeId);
                if (catchDef == null) {
                    instance.fail("Catch event definition missing: " + nodeId);
                    return false;
                }
                instance.waitTokenAtSignal(token, nodeId, catchDef.signalName());
                return false;
            }
            case "endEvent" -> {
                if (process.isSubProcessEnd(nodeId)) {
                    return exitSubProcessToNext(instance, process, token, process.subProcessForEndEvent(nodeId));
                }
                token.complete();
                instance.removeToken(token);
                if (instance.tokens().isEmpty()) {
                    instance.complete();
                } else {
                    instance.recomputeStatus();
                }
                return false;
            }
            default -> {
                instance.fail("Unsupported node type: " + nodeType);
                return false;
            }
        }
    }

    private boolean handleParallelGateway(
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
            return false;
        }
        if (process.isParallelJoin(nodeId)) {
            handleParallelJoin(instance, process, token, nodeId, executor, messageExecutor, evaluator);
            return false;
        }
        return advanceTokenToNext(instance, process, token, nodeId, evaluator);
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

    private void drain(
            WorkflowInstance instance,
            BpmnProcess process,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        withSyncBudget(() -> {
            while (instance.status() == InstanceStatus.RUNNING) {
                step(instance, process, executor, messageExecutor, evaluator);
            }
        });
    }

    private void withSyncBudget(SyncBody body) throws WorkflowException {
        boolean owner = SYNC_STEP_COUNT.get() == null;
        if (owner) {
            SYNC_STEP_COUNT.set(new int[]{0});
        }
        try {
            body.run();
        } finally {
            if (owner) {
                SYNC_STEP_COUNT.remove();
            }
        }
    }

    private boolean consumeSyncStep(WorkflowInstance instance) {
        int[] count = SYNC_STEP_COUNT.get();
        if (count == null || count[0] >= MAX_SYNC_STEPS) {
            instance.fail("Exceeded " + MAX_SYNC_STEPS + " synchronous workflow steps");
            return false;
        }
        count[0]++;
        return true;
    }

    @FunctionalInterface
    private interface SyncBody {
        void run() throws WorkflowException;
    }

    private void runBranchUntilBlocked(
            WorkflowInstance instance,
            BpmnProcess process,
            ExecutionToken token,
            WorkflowActionExecutor executor,
            MessageTaskExecutor messageExecutor,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        withSyncBudget(() -> {
            while (token.state() == TokenState.ACTIVE && instance.status() == InstanceStatus.RUNNING) {
                stepToken(instance, process, token, executor, messageExecutor, evaluator);
            }
        });
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
        if (advanceTokenToNext(instance, process, token, nodeId, evaluator)) {
            stepToken(instance, process, token, executor, messageExecutor, evaluator);
        }
    }

    private boolean advanceTokenToNext(
            WorkflowInstance instance,
            BpmnProcess process,
            ExecutionToken token,
            String nodeId,
            WorkflowConditionEvaluator evaluator
    ) throws WorkflowException {
        if (token.state() != TokenState.ACTIVE) {
            return false;
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
            return false;
        }

        instance.moveToken(token, next);
        return resolveArrivalOnNode(instance, process, token, next);
    }

    private boolean resolveArrivalOnNode(
            WorkflowInstance instance,
            BpmnProcess process,
            ExecutionToken token,
            String arrivedNodeId
    ) {
        if (process.isSubProcess(arrivedNodeId)) {
            SubProcessDefinition subProcess = process.subProcesses().get(arrivedNodeId);
            if (subProcess == null) {
                instance.fail("Subprocess definition missing: " + arrivedNodeId);
                return false;
            }
            instance.moveToken(token, subProcess.startNodeId());
            return true;
        }
        if (process.isEnd(arrivedNodeId)) {
            if (process.isSubProcessEnd(arrivedNodeId)) {
                return exitSubProcessToNext(instance, process, token, process.subProcessForEndEvent(arrivedNodeId));
            }
            token.complete();
            instance.removeToken(token);
            if (instance.tokens().isEmpty()) {
                instance.complete();
            }
            return false;
        }
        return true;
    }

    private boolean enterSubProcessToNext(
            WorkflowInstance instance,
            BpmnProcess process,
            ExecutionToken token,
            String subProcessNodeId
    ) {
        return resolveArrivalOnNode(instance, process, token, subProcessNodeId);
    }

    private boolean exitSubProcessToNext(
            WorkflowInstance instance,
            BpmnProcess process,
            ExecutionToken token,
            String subProcessNodeId
    ) {
        SubProcessDefinition subProcess = process.subProcesses().get(subProcessNodeId);
        if (subProcess == null) {
            instance.fail("Subprocess definition missing: " + subProcessNodeId);
            return false;
        }
        List<SequenceFlowDefinition> outgoing = process.outgoingFrom(subProcessNodeId);
        if (outgoing.isEmpty()) {
            instance.fail("Subprocess has no outgoing flow: " + subProcessNodeId);
            return false;
        }
        String next = outgoing.getFirst().targetRef();
        instance.moveToken(token, next);
        return true;
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
