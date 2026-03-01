package ai.teneta.argus.hitl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class HitlService {

    private static final Logger log = LoggerFactory.getLogger(HitlService.class);

    private final ConcurrentHashMap<UUID, CompletableFuture<ApprovalStatus>> pending = new ConcurrentHashMap<>();
    private final HitlNotificationChannel channel;
    private final long timeoutMinutes;

    public HitlService(
            HitlNotificationChannel channel,
            @Value("${argus.hitl.slack.approval-timeout-minutes:15}") long timeoutMinutes) {
        this.channel = channel;
        this.timeoutMinutes = timeoutMinutes;
    }

    public void requestApproval(UUID agentRunId, String toolName, Object params) {
        UUID requestId = UUID.randomUUID();
        ApprovalRequest req = new ApprovalRequest(
                requestId, agentRunId, toolName, params,
                Instant.now().plus(timeoutMinutes, ChronoUnit.MINUTES));
        CompletableFuture<ApprovalStatus> future = new CompletableFuture<>();
        pending.put(requestId, future);
        channel.sendApprovalRequest(req);

        ApprovalStatus decision;
        try {
            decision = future.get(timeoutMinutes, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            decision = ApprovalStatus.TIMED_OUT;
        } catch (Exception e) {
            decision = ApprovalStatus.TIMED_OUT;
            log.error("Error waiting for HITL approval: {}", e.getMessage(), e);
        } finally {
            pending.remove(requestId);
        }

        channel.updateWithDecision(requestId.toString(), decision, null);

        if (decision != ApprovalStatus.APPROVED) {
            throw new ApprovalDeniedException(toolName, decision);
        }
    }

    public void resolve(UUID requestId, ApprovalStatus decision, String decidedBy) {
        log.info("HITL resolved: requestId={}, decision={}, decidedBy={}", requestId, decision, decidedBy);
        Optional.ofNullable(pending.get(requestId))
                .ifPresent(f -> f.complete(decision));
    }

    // Visible for testing
    int pendingCount() {
        return pending.size();
    }
}
