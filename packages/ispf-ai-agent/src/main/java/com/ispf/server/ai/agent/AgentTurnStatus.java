package com.ispf.server.ai.agent;

public final class AgentTurnStatus {

    public static final String OK = "OK";
    public static final String ERROR = "ERROR";
    public static final String AWAITING_CONTINUE = "AWAITING_CONTINUE";
    public static final String CANCELLED = "CANCELLED";
    public static final String STOPPED = "STOPPED";

    private AgentTurnStatus() {
    }
}
