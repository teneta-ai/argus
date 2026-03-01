package ai.teneta.argus.agent;

import ai.teneta.argus.shared.AgentType;
import ai.teneta.argus.shared.RunContext;

import java.util.UUID;

/**
 * Concrete run context created by AgentOrchestrator when dispatching an agent run.
 * Implements the shared RunContext interface so that downstream modules (tool, security)
 * can read context without depending on the agent module.
 */
public record AgentRunContext(UUID runId, AgentType agentType) implements RunContext {
}
