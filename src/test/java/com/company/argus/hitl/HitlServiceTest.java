package com.company.argus.hitl;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HitlServiceTest {

    @Test
    void approveResolvesAndAllowsExecution() {
        HitlNotificationChannel channel = mock(HitlNotificationChannel.class);
        HitlService service = new HitlService(channel, 1);

        UUID agentRunId = UUID.randomUUID();

        // Simulate approval arriving before timeout on a separate thread
        doAnswer(inv -> {
            CompletableFuture.runAsync(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                service.resolve(agentRunId, ApprovalStatus.APPROVED, "test-user");
            });
            return null;
        }).when(channel).sendApprovalRequest(any());

        // Should not throw
        assertDoesNotThrow(() -> service.requestApproval(agentRunId, "jira_create_issue", "{}"));

        verify(channel).sendApprovalRequest(any());
        verify(channel).updateWithDecision(eq(agentRunId.toString()), eq(ApprovalStatus.APPROVED), any());
        assertEquals(0, service.pendingCount());
    }

    @Test
    void rejectThrowsApprovalDeniedException() {
        HitlNotificationChannel channel = mock(HitlNotificationChannel.class);
        HitlService service = new HitlService(channel, 1);

        UUID agentRunId = UUID.randomUUID();

        doAnswer(inv -> {
            CompletableFuture.runAsync(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                service.resolve(agentRunId, ApprovalStatus.REJECTED, "test-user");
            });
            return null;
        }).when(channel).sendApprovalRequest(any());

        ApprovalDeniedException ex = assertThrows(ApprovalDeniedException.class,
                () -> service.requestApproval(agentRunId, "jira_create_issue", "{}"));

        assertEquals(ApprovalStatus.REJECTED, ex.getStatus());
        assertEquals(0, service.pendingCount());
    }
}
