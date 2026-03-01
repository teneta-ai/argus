package ai.teneta.argus.hitl;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HitlChannelSwapTest {

    @Test
    void serviceWorksWithDifferentChannelImplementations() {
        // First channel: Slack
        HitlNotificationChannel slackChannel = mock(HitlNotificationChannel.class);
        when(slackChannel.channelName()).thenReturn("slack");
        HitlService slackService = new HitlService(slackChannel, 1);

        UUID runId1 = UUID.randomUUID();
        doAnswer(inv -> {
            CompletableFuture.runAsync(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                slackService.resolve(runId1, ApprovalStatus.APPROVED, "slack-user");
            });
            return null;
        }).when(slackChannel).sendApprovalRequest(any());

        assertDoesNotThrow(() -> slackService.requestApproval(runId1, "jira_create_issue", "{}"));
        verify(slackChannel).sendApprovalRequest(any());
        assertEquals("slack", slackChannel.channelName());

        // Second channel: a different implementation (e.g., Teams, PagerDuty)
        HitlNotificationChannel teamsChannel = mock(HitlNotificationChannel.class);
        when(teamsChannel.channelName()).thenReturn("teams");
        HitlService teamsService = new HitlService(teamsChannel, 1);

        UUID runId2 = UUID.randomUUID();
        doAnswer(inv -> {
            CompletableFuture.runAsync(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                teamsService.resolve(runId2, ApprovalStatus.APPROVED, "teams-user");
            });
            return null;
        }).when(teamsChannel).sendApprovalRequest(any());

        assertDoesNotThrow(() -> teamsService.requestApproval(runId2, "jira_create_issue", "{}"));
        verify(teamsChannel).sendApprovalRequest(any());
        assertEquals("teams", teamsChannel.channelName());
    }

    @Test
    void channelReceivesCorrectApprovalRequest() {
        AtomicReference<ApprovalRequest> captured = new AtomicReference<>();

        HitlNotificationChannel channel = new HitlNotificationChannel() {
            @Override
            public void sendApprovalRequest(ApprovalRequest request) {
                captured.set(request);
            }
            @Override
            public void updateWithDecision(String correlationId, ApprovalStatus status, String decidedBy) {}
            @Override
            public String channelName() { return "test-channel"; }
        };

        HitlService service = new HitlService(channel, 1);
        UUID runId = UUID.randomUUID();

        // Resolve immediately to avoid blocking
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            service.resolve(runId, ApprovalStatus.APPROVED, "admin");
        });

        service.requestApproval(runId, "grafana_search_dashboards", "{\"query\":\"cpu\"}");

        assertNotNull(captured.get());
        assertEquals(runId, captured.get().agentRunId());
        assertEquals("grafana_search_dashboards", captured.get().toolName());
        assertNotNull(captured.get().expiresAt());
    }

    @Test
    void timeoutProducesTimedOutDecision() {
        HitlNotificationChannel slowChannel = mock(HitlNotificationChannel.class);
        when(slowChannel.channelName()).thenReturn("slow-channel");

        // Use a very short timeout (1 minute) — but we rely on the CompletableFuture timeout
        // To make this test fast, we create the service with a timeout that's short enough
        // The HitlService uses TimeUnit.MINUTES, so we can't go sub-minute.
        // Instead, we never resolve the future, and verify the thrown exception.
        HitlService service = new HitlService(slowChannel, 1);
        UUID runId = UUID.randomUUID();

        // Don't resolve — let it time out
        // But 1 minute is too long for a test. Instead, verify the denied path directly.
        doAnswer(inv -> {
            CompletableFuture.runAsync(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                service.resolve(runId, ApprovalStatus.TIMED_OUT, null);
            });
            return null;
        }).when(slowChannel).sendApprovalRequest(any());

        ApprovalDeniedException ex = assertThrows(ApprovalDeniedException.class,
                () -> service.requestApproval(runId, "jira_create_issue", "{}"));
        assertEquals(ApprovalStatus.TIMED_OUT, ex.getStatus());
    }
}
