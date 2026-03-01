package ai.teneta.argus.tool;

public record ToolAllowListEntry(
        String agentType,
        String toolName,
        AccessLevel accessLevel,
        boolean requiresHitl
) {

    public enum AccessLevel {
        READ, READ_WRITE
    }
}
