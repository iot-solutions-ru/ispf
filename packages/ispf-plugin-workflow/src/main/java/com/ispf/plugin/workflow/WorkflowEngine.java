package com.ispf.plugin.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WorkflowEngine {

    private final BpmnParser parser = new BpmnParser();

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
                try {
                    executor.execute(task, instance);
                } catch (WorkflowException e) {
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
                try {
                    messageExecutor.execute(task, instance);
                } catch (WorkflowException e) {
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
            }
            case "intermediateCatchEvent" -> {
                SignalCatchDefinition catchDef = process.signalCatchEvents().get(nodeId);
                if (catchDef == null) {
                    instance.fail("Signal catch definition missing: " + nodeId);
                    return;
                }
                instance.waitTokenAtSignal(token, nodeId, catchDef.signalName());
            }
            case "endEvent" -> {
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
        if (process.isEnd(next)) {
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

    private static java.util.Optional<ExecutionToken> findWaitingToken(
            WorkflowInstance instance,
            String userTaskNodeId
    ) {
        return instance.waitingTokens().stream()
                .filter(token -> userTaskNodeId.equals(token.pendingUserTaskId()))
                .findFirst();
    }
}
