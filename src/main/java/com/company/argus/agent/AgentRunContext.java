package com.company.argus.agent;

import com.company.argus.shared.AgentType;
import com.company.argus.shared.RunContext;

import java.util.UUID;

/**
 * Concrete run context created by AgentOrchestrator when dispatching an agent run.
 * Implements the shared RunContext interface so that downstream modules (tool, security)
 * can read context without depending on the agent module.
 */
public record AgentRunContext(UUID runId, AgentType agentType) implements RunContext {
}
