package ai.teneta.argus.hitl;

import java.time.Instant;
import java.util.UUID;

public record ApprovalRequest(
        UUID requestId,
        UUID agentRunId,
        String toolName,
        Object params,
        Instant expiresAt
) {
}
