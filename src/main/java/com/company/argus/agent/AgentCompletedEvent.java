package com.company.argus.agent;

import com.company.argus.AgentType;

import java.time.Instant;
import java.util.UUID;

public record AgentCompletedEvent(
        UUID agentRunId,
        AgentType agentType,
        String result,
        Instant completedAt
) {
}
