package com.company.argus;

import java.util.UUID;

public final class AgentRunContext {

    private static final ThreadLocal<AgentRunContext> CURRENT = new ThreadLocal<>();

    private final UUID agentRunId;
    private final AgentType agentType;

    public AgentRunContext(UUID agentRunId, AgentType agentType) {
        this.agentRunId = agentRunId;
        this.agentType = agentType;
    }

    public UUID getAgentRunId() {
        return agentRunId;
    }

    public AgentType getAgentType() {
        return agentType;
    }

    public static AgentRunContext current() {
        AgentRunContext ctx = CURRENT.get();
        if (ctx == null) {
            throw new IllegalStateException("No AgentRunContext set on current thread");
        }
        return ctx;
    }

    public static void set(AgentRunContext context) {
        CURRENT.set(context);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
