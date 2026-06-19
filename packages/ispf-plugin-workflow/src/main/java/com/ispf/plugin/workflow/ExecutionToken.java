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
    private String arrivedJoinNodeId;

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

    public String arrivedJoinNodeId() {
        return arrivedJoinNodeId;
    }

    public void moveTo(String nodeId) {
        currentNodeId = nodeId;
    }

    public void waitAtUserTask(String userTaskNodeId) {
        state = TokenState.WAITING;
        pendingUserTaskId = userTaskNodeId;
        pendingSignalCatchNodeId = null;
        pendingSignalName = null;
        currentNodeId = userTaskNodeId;
    }

    public void waitAtSignalCatch(String catchNodeId, String signalName) {
        state = TokenState.WAITING;
        pendingUserTaskId = null;
        pendingSignalCatchNodeId = catchNodeId;
        pendingSignalName = signalName;
        currentNodeId = catchNodeId;
    }

    public void resumeAfterUserTask() {
        state = TokenState.ACTIVE;
        pendingUserTaskId = null;
    }

    public void resumeAfterSignalCatch() {
        state = TokenState.ACTIVE;
        pendingSignalCatchNodeId = null;
        pendingSignalName = null;
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
        arrivedJoinNodeId = null;
    }

    public void activateAt(String nodeId) {
        state = TokenState.ACTIVE;
        currentNodeId = nodeId;
        pendingUserTaskId = null;
        pendingSignalCatchNodeId = null;
        pendingSignalName = null;
        arrivedJoinNodeId = null;
    }

    public static ExecutionToken restore(
            String tokenId,
            String currentNodeId,
            TokenState state,
            String pendingUserTaskId,
            String pendingSignalCatchNodeId,
            String pendingSignalName,
            String arrivedJoinNodeId
    ) {
        ExecutionToken token = new ExecutionToken(tokenId, currentNodeId);
        token.state = state;
        token.pendingUserTaskId = pendingUserTaskId;
        token.pendingSignalCatchNodeId = pendingSignalCatchNodeId;
        token.pendingSignalName = pendingSignalName;
        token.arrivedJoinNodeId = arrivedJoinNodeId;
        return token;
    }
}
