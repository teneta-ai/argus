package ai.teneta.argus.agent;

import ai.teneta.argus.shared.AgentType;

import java.time.Instant;
import java.util.UUID;

public record AgentCompletedEvent(
        UUID agentRunId,
        AgentType agentType,
        String result,
        Instant completedAt
) {
}
