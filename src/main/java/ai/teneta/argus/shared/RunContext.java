package ai.teneta.argus.shared;

import java.util.UUID;

/**
 * Read-only view of the current agent execution context.
 * Set by the agent module's orchestrator, consumed by tool and security modules
 * to make authorization and audit decisions without depending on agent internals.
 */
public interface RunContext {

    UUID runId();

    AgentType agentType();
}
