package com.ispf.plugin.workflow;

import java.util.UUID;

/**
 * A single execution path inside a workflow instance (supports parallel branches).
 */
public final class ExecutionToken {

    private final String tokenId;
    private String currentNodeId;
    private TokenState state;
    private String pendingUserTaskId;
    private String pendingSignalCatchNodeId;
    private String pendingSignalName;
    private String pendingMessageCatchNodeId;
    private String pendingMessageName;
    private String pendingTimerCatchNodeId;
    private String pendingBoundaryTimerNodeId;
    private long timerDeadlineEpochMs;
    private String arrivedJoinNodeId;
    private String pendingCallActivityNodeId;
    private String pendingCallChildInstanceId;

    public ExecutionToken(String tokenId, String startNodeId) {
        this.tokenId = tokenId;
        this.currentNodeId = startNodeId;
        this.state = TokenState.ACTIVE;
    }

    public static ExecutionToken create(String startNodeId) {
        return new ExecutionToken(UUID.randomUUID().toString(), startNodeId);
    }

    public String tokenId() {
        return tokenId;
    }

    public String currentNodeId() {
        return currentNodeId;
    }

    public TokenState state() {
        return state;
    }

    public String pendingUserTaskId() {
        return pendingUserTaskId;
    }

    public String pendingSignalCatchNodeId() {
        return pendingSignalCatchNodeId;
    }

    public String pendingSignalName() {
        return pendingSignalName;
    }

    public String pendingMessageCatchNodeId() {
        return pendingMessageCatchNodeId;
    }

    public String pendingMessageName() {
        return pendingMessageName;
    }

    public String pendingTimerCatchNodeId() {
        return pendingTimerCatchNodeId;
    }

    public String pendingBoundaryTimerNodeId() {
        return pendingBoundaryTimerNodeId;
    }

    public long timerDeadlineEpochMs() {
        return timerDeadlineEpochMs;
    }

    public String arrivedJoinNodeId() {
        return arrivedJoinNodeId;
    }

    public String pendingCallActivityNodeId() {
        return pendingCallActivityNodeId;
    }

    public String pendingCallChildInstanceId() {
        return pendingCallChildInstanceId;
    }

    public void moveTo(String nodeId) {
        currentNodeId = nodeId;
    }

    public void waitAtUserTask(String userTaskNodeId) {
        state = TokenState.WAITING;
        pendingUserTaskId = userTaskNodeId;
        pendingSignalCatchNodeId = null;
        pendingSignalName = null;
        pendingMessageCatchNodeId = null;
        pendingMessageName = null;
        pendingTimerCatchNodeId = null;
        pendingCallActivityNodeId = null;
        pendingCallChildInstanceId = null;
        currentNodeId = userTaskNodeId;
    }

    public void waitAtSignalCatch(String catchNodeId, String signalName) {
        state = TokenState.WAITING;
        pendingUserTaskId = null;
        pendingSignalCatchNodeId = catchNodeId;
        pendingSignalName = signalName;
        pendingMessageCatchNodeId = null;
        pendingMessageName = null;
        pendingTimerCatchNodeId = null;
        pendingBoundaryTimerNodeId = null;
        timerDeadlineEpochMs = 0L;
        pendingCallActivityNodeId = null;
        pendingCallChildInstanceId = null;
        currentNodeId = catchNodeId;
    }

    public void waitAtMessageCatch(String catchNodeId, String messageName) {
        state = TokenState.WAITING;
        pendingUserTaskId = null;
        pendingSignalCatchNodeId = null;
        pendingSignalName = null;
        pendingMessageCatchNodeId = catchNodeId;
        pendingMessageName = messageName;
        pendingTimerCatchNodeId = null;
        pendingBoundaryTimerNodeId = null;
        timerDeadlineEpochMs = 0L;
        pendingCallActivityNodeId = null;
        pendingCallChildInstanceId = null;
        currentNodeId = catchNodeId;
    }

    public void waitAtTimerCatch(String catchNodeId, long deadlineEpochMs) {
        state = TokenState.WAITING;
        pendingUserTaskId = null;
        pendingSignalCatchNodeId = null;
        pendingSignalName = null;
        pendingMessageCatchNodeId = null;
        pendingMessageName = null;
        pendingTimerCatchNodeId = catchNodeId;
        pendingBoundaryTimerNodeId = null;
        timerDeadlineEpochMs = deadlineEpochMs;
        pendingCallActivityNodeId = null;
        pendingCallChildInstanceId = null;
        currentNodeId = catchNodeId;
    }

    public void scheduleBoundaryTimer(String boundaryNodeId, long deadlineEpochMs) {
        pendingBoundaryTimerNodeId = boundaryNodeId;
        timerDeadlineEpochMs = deadlineEpochMs;
    }

    public void clearBoundaryTimer() {
        pendingBoundaryTimerNodeId = null;
        timerDeadlineEpochMs = 0L;
    }

    public void resumeAfterUserTask() {
        state = TokenState.ACTIVE;
        pendingUserTaskId = null;
        clearBoundaryTimer();
    }

    public void resumeAfterSignalCatch() {
        state = TokenState.ACTIVE;
        pendingSignalCatchNodeId = null;
        pendingSignalName = null;
    }

    public void resumeAfterMessageCatch() {
        state = TokenState.ACTIVE;
        pendingMessageCatchNodeId = null;
        pendingMessageName = null;
    }

    public void resumeAfterTimerCatch() {
        state = TokenState.ACTIVE;
        pendingTimerCatchNodeId = null;
        timerDeadlineEpochMs = 0L;
    }

    public void resumeAfterBoundaryTimer(String boundaryNodeId) {
        state = TokenState.ACTIVE;
        pendingUserTaskId = null;
        pendingBoundaryTimerNodeId = null;
        timerDeadlineEpochMs = 0L;
        currentNodeId = boundaryNodeId;
    }

    public void waitAtCallActivity(String callNodeId, String childInstanceId) {
        state = TokenState.WAITING;
        pendingUserTaskId = null;
        pendingSignalCatchNodeId = null;
        pendingSignalName = null;
        pendingMessageCatchNodeId = null;
        pendingMessageName = null;
        pendingTimerCatchNodeId = null;
        pendingBoundaryTimerNodeId = null;
        timerDeadlineEpochMs = 0L;
        pendingCallActivityNodeId = callNodeId;
        pendingCallChildInstanceId = childInstanceId;
        currentNodeId = callNodeId;
    }

    public void resumeAfterCallActivity() {
        state = TokenState.ACTIVE;
        pendingCallActivityNodeId = null;
        pendingCallChildInstanceId = null;
    }

    public void arriveAtJoin(String joinNodeId) {
        state = TokenState.AT_JOIN;
        arrivedJoinNodeId = joinNodeId;
        currentNodeId = joinNodeId;
    }

    public void complete() {
        state = TokenState.COMPLETED;
        pendingUserTaskId = null;
        pendingSignalCatchNodeId = null;
        pendingSignalName = null;
        pendingMessageCatchNodeId = null;
        pendingMessageName = null;
        pendingTimerCatchNodeId = null;
        pendingBoundaryTimerNodeId = null;
        timerDeadlineEpochMs = 0L;
        arrivedJoinNodeId = null;
        pendingCallActivityNodeId = null;
        pendingCallChildInstanceId = null;
    }

    public void activateAt(String nodeId) {
        state = TokenState.ACTIVE;
        currentNodeId = nodeId;
        pendingUserTaskId = null;
        pendingSignalCatchNodeId = null;
        pendingSignalName = null;
        pendingMessageCatchNodeId = null;
        pendingMessageName = null;
        pendingTimerCatchNodeId = null;
        pendingBoundaryTimerNodeId = null;
        timerDeadlineEpochMs = 0L;
        arrivedJoinNodeId = null;
        pendingCallActivityNodeId = null;
        pendingCallChildInstanceId = null;
    }

    public static ExecutionToken restore(
            String tokenId,
            String currentNodeId,
            TokenState state,
            String pendingUserTaskId,
            String pendingSignalCatchNodeId,
            String pendingSignalName,
            String pendingMessageCatchNodeId,
            String pendingMessageName,
            String pendingTimerCatchNodeId,
            String pendingBoundaryTimerNodeId,
            long timerDeadlineEpochMs,
            String arrivedJoinNodeId,
            String pendingCallActivityNodeId,
            String pendingCallChildInstanceId
    ) {
        ExecutionToken token = new ExecutionToken(tokenId, currentNodeId);
        token.state = state;
        token.pendingUserTaskId = pendingUserTaskId;
        token.pendingSignalCatchNodeId = pendingSignalCatchNodeId;
        token.pendingSignalName = pendingSignalName;
        token.pendingMessageCatchNodeId = pendingMessageCatchNodeId;
        token.pendingMessageName = pendingMessageName;
        token.pendingTimerCatchNodeId = pendingTimerCatchNodeId;
        token.pendingBoundaryTimerNodeId = pendingBoundaryTimerNodeId;
        token.timerDeadlineEpochMs = timerDeadlineEpochMs;
        token.arrivedJoinNodeId = arrivedJoinNodeId;
        token.pendingCallActivityNodeId = pendingCallActivityNodeId;
        token.pendingCallChildInstanceId = pendingCallChildInstanceId;
        return token;
    }
}
