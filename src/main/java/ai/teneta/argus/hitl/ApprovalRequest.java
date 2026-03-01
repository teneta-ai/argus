package ai.teneta.argus.hitl;

import java.time.Instant;
import java.util.UUID;

public record ApprovalRequest(
        UUID agentRunId,
        String toolName,
        Object params,
        Instant expiresAt
) {
}
