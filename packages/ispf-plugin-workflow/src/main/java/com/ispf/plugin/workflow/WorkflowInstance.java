package com.ispf.plugin.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class WorkflowInstance {

    private final String instanceId;
    private final String workflowPath;
    private InstanceStatus status;
    private final Instant startedAt;
    private Instant completedAt;
    private final List<String> history = new ArrayList<>();
    private String errorMessage;
    private String assignee;
    private final Map<String, String> variables = new HashMap<>();
    private final List<ExecutionToken> tokens = new ArrayList<>();

    public WorkflowInstance(String instanceId, String workflowPath, String startNodeId) {
        this(instanceId, workflowPath, startNodeId, Instant.now());
    }

    private WorkflowInstance(String instanceId, String workflowPath, String startNodeId, Instant startedAt) {
        this.instanceId = instanceId;
        this.workflowPath = workflowPath;
        this.status = InstanceStatus.RUNNING;
        this.startedAt = startedAt;
        tokens.add(ExecutionToken.create(startNodeId));
        history.add("START@" + startNodeId);
    }

    public static WorkflowInstance restore(
            String instanceId,
            String workflowPath,
            InstanceStatus status,
            String currentNodeId,
            Instant startedAt,
            Instant completedAt,
            String assignee,
            String pendingUserTaskId,
            List<String> history,
            Map<String, String> variables,
            String errorMessage,
            List<ExecutionToken> restoredTokens
    ) {
        WorkflowInstance instance = new WorkflowInstance(instanceId, workflowPath, currentNodeId, startedAt);
        instance.status = status;
        instance.completedAt = completedAt;
        instance.assignee = assignee;
        instance.errorMessage = errorMessage;
        instance.history.clear();
        instance.history.addAll(history);
        instance.variables.putAll(variables);
        instance.tokens.clear();
        if (restoredTokens == null || restoredTokens.isEmpty()) {
            ExecutionToken token = ExecutionToken.create(currentNodeId);
            if (status == InstanceStatus.WAITING && pendingUserTaskId != null) {
                token.waitAtUserTask(pendingUserTaskId);
            }
            instance.tokens.add(token);
        } else {
            instance.tokens.addAll(restoredTokens);
        }
        instance.recomputeStatus();
        return instance;
    }

    public String instanceId() {
        return instanceId;
    }

    public String workflowPath() {
        return workflowPath;
    }

    public InstanceStatus status() {
        return status;
    }

    public String currentNodeId() {
        return tokens.stream()
                .filter(token -> token.state() != TokenState.COMPLETED)
                .map(ExecutionToken::currentNodeId)
                .findFirst()
                .orElse(tokens.isEmpty() ? null : tokens.getLast().currentNodeId());
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public List<String> history() {
        return List.copyOf(history);
    }

    public String errorMessage() {
        return errorMessage;
    }

    public Optional<String> pendingUserTaskId() {
        return waitingTokens().stream()
                .map(ExecutionToken::pendingUserTaskId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst();
    }

    public List<String> pendingUserTaskIds() {
        return waitingTokens().stream()
                .map(ExecutionToken::pendingUserTaskId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
    }

    public Optional<String> pendingSignalName() {
        return waitingTokens().stream()
                .map(ExecutionToken::pendingSignalName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst();
    }

    public List<String> pendingSignalNames() {
        return waitingTokens().stream()
                .map(ExecutionToken::pendingSignalName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }

    public Optional<String> pendingMessageName() {
        return waitingTokens().stream()
                .map(ExecutionToken::pendingMessageName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst();
    }

    public List<String> pendingMessageNames() {
        return waitingTokens().stream()
                .map(ExecutionToken::pendingMessageName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }

    public boolean hasDueTimers(long nowEpochMs) {
        return waitingTokens().stream()
                .anyMatch(token -> token.timerDeadlineEpochMs() > 0L && nowEpochMs >= token.timerDeadlineEpochMs());
    }

    public List<ExecutionToken> dueTimerTokens(long nowEpochMs) {
        List<ExecutionToken> due = new ArrayList<>();
        for (ExecutionToken token : waitingTokens()) {
            if (token.timerDeadlineEpochMs() > 0L && nowEpochMs >= token.timerDeadlineEpochMs()) {
                due.add(token);
            }
        }
        return due;
    }

    public Optional<String> assignee() {
        return Optional.ofNullable(assignee);
    }

    public Map<String, String> variables() {
        return Map.copyOf(variables);
    }

    public List<ExecutionToken> tokens() {
        return List.copyOf(tokens);
    }

    public List<ExecutionToken> activeTokens() {
        return tokens.stream()
                .filter(token -> token.state() == TokenState.ACTIVE)
                .toList();
    }

    public List<ExecutionToken> waitingTokens() {
        return tokens.stream()
                .filter(token -> token.state() == TokenState.WAITING)
                .toList();
    }

    public List<ExecutionToken> tokensAtJoin(String joinNodeId) {
        return tokens.stream()
                .filter(token -> token.state() == TokenState.AT_JOIN && joinNodeId.equals(token.arrivedJoinNodeId()))
                .toList();
    }

    public void setVariable(String name, String value) {
        variables.put(name, value);
    }

    public ExecutionToken createToken(String startNodeId) {
        ExecutionToken token = ExecutionToken.create(startNodeId);
        tokens.add(token);
        return token;
    }

    public void moveTo(String nodeId) {
        ExecutionToken token = primaryToken();
        token.moveTo(nodeId);
        history.add("NODE@" + nodeId);
    }

    public void moveToken(ExecutionToken token, String nodeId) {
        token.moveTo(nodeId);
        history.add("NODE@" + nodeId + "#" + token.tokenId());
    }

    public void waitAt(String userTaskNodeId) {
        ExecutionToken token = primaryToken();
        token.waitAtUserTask(userTaskNodeId);
        history.add("WAITING@" + userTaskNodeId);
        recomputeStatus();
    }

    public void waitTokenAt(ExecutionToken token, String userTaskNodeId) {
        token.waitAtUserTask(userTaskNodeId);
        history.add("WAITING@" + userTaskNodeId + "#" + token.tokenId());
        recomputeStatus();
    }

    public void waitTokenAtSignal(ExecutionToken token, String catchNodeId, String signalName) {
        token.waitAtSignalCatch(catchNodeId, signalName);
        history.add("SIGNAL_WAIT@" + signalName + "#" + token.tokenId());
        recomputeStatus();
    }

    public void waitTokenAtMessage(ExecutionToken token, String catchNodeId, String messageName) {
        token.waitAtMessageCatch(catchNodeId, messageName);
        history.add("MESSAGE_WAIT@" + messageName + "#" + token.tokenId());
        recomputeStatus();
    }

    public void waitTokenAtTimer(ExecutionToken token, String catchNodeId, long deadlineEpochMs) {
        token.waitAtTimerCatch(catchNodeId, deadlineEpochMs);
        history.add("TIMER_WAIT@" + catchNodeId + "#" + token.tokenId());
        recomputeStatus();
    }

    public void scheduleBoundaryTimer(ExecutionToken token, String boundaryNodeId, long deadlineEpochMs) {
        token.scheduleBoundaryTimer(boundaryNodeId, deadlineEpochMs);
        history.add("BOUNDARY_TIMER@" + boundaryNodeId + "#" + token.tokenId());
    }

    public void claim(String operatorId) {
        assignee = operatorId;
        history.add("CLAIMED@" + operatorId);
    }

    public void resumeUserTask(String userTaskNodeId) {
        for (ExecutionToken token : waitingTokens()) {
            if (userTaskNodeId.equals(token.pendingUserTaskId())) {
                token.resumeAfterUserTask();
                history.add("RESUMED@" + userTaskNodeId + "#" + token.tokenId());
                recomputeStatus();
                return;
            }
        }
        throw new IllegalStateException("No waiting token for user task: " + userTaskNodeId);
    }

    public List<ExecutionToken> resumeSignal(String signalName) {
        List<ExecutionToken> resumed = new ArrayList<>();
        for (ExecutionToken token : waitingTokens()) {
            if (signalName.equals(token.pendingSignalName())) {
                token.resumeAfterSignalCatch();
                history.add("SIGNAL@" + signalName + "#" + token.tokenId());
                resumed.add(token);
            }
        }
        if (resumed.isEmpty()) {
            throw new IllegalStateException("No waiting token for signal: " + signalName);
        }
        recomputeStatus();
        return resumed;
    }

    public List<ExecutionToken> resumeMessageIfPresent(String messageName) {
        List<ExecutionToken> resumed = new ArrayList<>();
        for (ExecutionToken token : waitingTokens()) {
            if (messageName.equals(token.pendingMessageName())) {
                token.resumeAfterMessageCatch();
                history.add("MESSAGE@" + messageName + "#" + token.tokenId());
                resumed.add(token);
            }
        }
        if (!resumed.isEmpty()) {
            recomputeStatus();
        }
        return resumed;
    }

    public List<ExecutionToken> resumeMessage(String messageName) {
        List<ExecutionToken> resumed = new ArrayList<>();
        for (ExecutionToken token : waitingTokens()) {
            if (messageName.equals(token.pendingMessageName())) {
                token.resumeAfterMessageCatch();
                history.add("MESSAGE@" + messageName + "#" + token.tokenId());
                resumed.add(token);
            }
        }
        if (resumed.isEmpty()) {
            throw new IllegalStateException("No waiting token for message: " + messageName);
        }
        recomputeStatus();
        return resumed;
    }

    public ExecutionToken resumeBoundaryTimer(String boundaryNodeId) {
        for (ExecutionToken token : waitingTokens()) {
            if (boundaryNodeId.equals(token.pendingBoundaryTimerNodeId())) {
                token.resumeAfterBoundaryTimer(boundaryNodeId);
                history.add("BOUNDARY_TIMER@" + boundaryNodeId + "#" + token.tokenId());
                recomputeStatus();
                return token;
            }
        }
        throw new IllegalStateException("No waiting token for boundary timer: " + boundaryNodeId);
    }

    public List<ExecutionToken> resumeTimerCatch(String catchNodeId) {
        List<ExecutionToken> resumed = new ArrayList<>();
        for (ExecutionToken token : waitingTokens()) {
            if (catchNodeId.equals(token.pendingTimerCatchNodeId())) {
                token.resumeAfterTimerCatch();
                history.add("TIMER@" + catchNodeId + "#" + token.tokenId());
                resumed.add(token);
            }
        }
        if (resumed.isEmpty()) {
            throw new IllegalStateException("No waiting token for timer catch: " + catchNodeId);
        }
        recomputeStatus();
        return resumed;
    }

    public ExecutionToken mergeTokensAtJoin(String joinNodeId) {
        List<ExecutionToken> arrived = new ArrayList<>(tokensAtJoin(joinNodeId));
        tokens.removeIf(token -> token.state() == TokenState.AT_JOIN && joinNodeId.equals(token.arrivedJoinNodeId()));
        ExecutionToken merged = ExecutionToken.create(joinNodeId);
        merged.activateAt(joinNodeId);
        tokens.add(merged);
        history.add("JOIN@" + joinNodeId + "#" + arrived.size());
        return merged;
    }

    public void removeToken(ExecutionToken token) {
        tokens.remove(token);
    }

    public void complete() {
        status = InstanceStatus.COMPLETED;
        completedAt = Instant.now();
        tokens.forEach(ExecutionToken::complete);
        history.add("COMPLETED");
    }

    public void fail(String message) {
        status = InstanceStatus.FAILED;
        completedAt = Instant.now();
        errorMessage = message;
        history.add("FAILED:" + message);
    }

    public void recomputeStatus() {
        if (status == InstanceStatus.FAILED || status == InstanceStatus.COMPLETED) {
            return;
        }
        boolean hasActive = tokens.stream().anyMatch(token -> token.state() == TokenState.ACTIVE);
        boolean hasWaiting = tokens.stream().anyMatch(token -> token.state() == TokenState.WAITING);
        boolean hasAtJoin = tokens.stream().anyMatch(token -> token.state() == TokenState.AT_JOIN);
        if (hasWaiting && !hasActive) {
            status = InstanceStatus.WAITING;
            return;
        }
        if (!hasActive && !hasWaiting && !hasAtJoin && !tokens.isEmpty()) {
            complete();
            return;
        }
        status = InstanceStatus.RUNNING;
    }

    private ExecutionToken primaryToken() {
        return tokens.stream()
                .filter(token -> token.state() != TokenState.COMPLETED)
                .findFirst()
                .orElse(tokens.getFirst());
    }

    public Map<String, Object> serializeTokens() {
        List<Map<String, String>> serialized = tokens.stream()
                .map(token -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("id", token.tokenId());
                    map.put("currentNodeId", token.currentNodeId());
                    map.put("state", token.state().name());
                    if (token.pendingUserTaskId() != null) {
                        map.put("pendingUserTaskId", token.pendingUserTaskId());
                    }
                    if (token.pendingSignalCatchNodeId() != null) {
                        map.put("pendingSignalCatchNodeId", token.pendingSignalCatchNodeId());
                    }
                    if (token.pendingSignalName() != null) {
                        map.put("pendingSignalName", token.pendingSignalName());
                    }
                    if (token.pendingMessageCatchNodeId() != null) {
                        map.put("pendingMessageCatchNodeId", token.pendingMessageCatchNodeId());
                    }
                    if (token.pendingMessageName() != null) {
                        map.put("pendingMessageName", token.pendingMessageName());
                    }
                    if (token.pendingTimerCatchNodeId() != null) {
                        map.put("pendingTimerCatchNodeId", token.pendingTimerCatchNodeId());
                    }
                    if (token.pendingBoundaryTimerNodeId() != null) {
                        map.put("pendingBoundaryTimerNodeId", token.pendingBoundaryTimerNodeId());
                    }
                    if (token.timerDeadlineEpochMs() > 0L) {
                        map.put("timerDeadlineEpochMs", String.valueOf(token.timerDeadlineEpochMs()));
                    }
                    if (token.arrivedJoinNodeId() != null) {
                        map.put("arrivedJoinNodeId", token.arrivedJoinNodeId());
                    }
                    return map;
                })
                .collect(Collectors.toList());
        return Map.of("tokens", serialized);
    }

    @SuppressWarnings("unchecked")
    public static List<ExecutionToken> deserializeTokens(Map<String, Object> state) {
        Object raw = state.get("tokens");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<ExecutionToken> restored = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String id = stringValue(map.get("id"));
            if (id == null) {
                id = UUID.randomUUID().toString();
            }
            String currentNodeId = stringValue(map.get("currentNodeId"));
            TokenState tokenState = TokenState.valueOf(stringValue(map.get("state")));
            long timerDeadline = 0L;
            String deadlineRaw = stringValue(map.get("timerDeadlineEpochMs"));
            if (deadlineRaw != null && !deadlineRaw.isBlank()) {
                timerDeadline = Long.parseLong(deadlineRaw);
            }
            restored.add(ExecutionToken.restore(
                    id,
                    currentNodeId,
                    tokenState,
                    stringValue(map.get("pendingUserTaskId")),
                    stringValue(map.get("pendingSignalCatchNodeId")),
                    stringValue(map.get("pendingSignalName")),
                    stringValue(map.get("pendingMessageCatchNodeId")),
                    stringValue(map.get("pendingMessageName")),
                    stringValue(map.get("pendingTimerCatchNodeId")),
                    stringValue(map.get("pendingBoundaryTimerNodeId")),
                    timerDeadline,
                    stringValue(map.get("arrivedJoinNodeId"))
            ));
        }
        return restored;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
