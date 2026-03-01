package ai.teneta.argus.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
        UUID agentRunId,
        String toolName,
        Status status,
        String detail,
        Instant timestamp
) {

    public enum Status {
        ATTEMPTED, SUCCESS, FAILED, BLOCKED
    }

    public static AuditEvent attempted(UUID agentRunId, String toolName, Object executionRequest) {
        return new AuditEvent(agentRunId, toolName, Status.ATTEMPTED,
                executionRequest != null ? executionRequest.toString() : "", Instant.now());
    }

    public static AuditEvent success(UUID agentRunId, String toolName, String result) {
        return new AuditEvent(agentRunId, toolName, Status.SUCCESS, truncate(result), Instant.now());
    }

    public static AuditEvent failed(UUID agentRunId, String toolName, String error) {
        return new AuditEvent(agentRunId, toolName, Status.FAILED, error, Instant.now());
    }

    public static AuditEvent blocked(UUID agentRunId, String toolName) {
        return new AuditEvent(agentRunId, toolName, Status.BLOCKED, "Not in allow-list", Instant.now());
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}
