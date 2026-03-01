package ai.teneta.argus.hitl;

public interface HitlNotificationChannel {

    void sendApprovalRequest(ApprovalRequest request);

    void updateWithDecision(String correlationId, ApprovalStatus status, String decidedBy);

    String channelName();
}
