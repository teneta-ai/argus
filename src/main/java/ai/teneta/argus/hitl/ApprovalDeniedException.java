package ai.teneta.argus.hitl;

public class ApprovalDeniedException extends RuntimeException {

    private final String toolName;
    private final ApprovalStatus status;

    public ApprovalDeniedException(String toolName, ApprovalStatus status) {
        super("Approval denied for tool " + toolName + ": " + status);
        this.toolName = toolName;
        this.status = status;
    }

    public String getToolName() {
        return toolName;
    }

    public ApprovalStatus getStatus() {
        return status;
    }
}
