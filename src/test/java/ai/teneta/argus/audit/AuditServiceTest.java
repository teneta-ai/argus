package ai.teneta.argus.audit;

import ai.teneta.argus.queue.QueueNames;
import ai.teneta.argus.queue.QueuePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditServiceTest {

    private QueuePort queuePort;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        queuePort = mock(QueuePort.class);
        auditService = new AuditService(queuePort);
    }

    @Test
    void publishSendsEventToAuditQueue() {
        AuditEvent event = new AuditEvent(
                UUID.randomUUID(), "jira_get_issue", AuditEvent.Status.SUCCESS,
                "Fetched issue", Instant.now());

        auditService.publish(event);

        verify(queuePort).publish(QueueNames.AUDIT, event);
    }

    @Test
    void publishSwallowsQueueFailure() {
        doThrow(new RuntimeException("SQS down"))
                .when(queuePort).publish(any(), any());

        AuditEvent event = new AuditEvent(
                UUID.randomUUID(), "jira_get_issue", AuditEvent.Status.SUCCESS,
                "Fetched issue", Instant.now());

        // Should not throw — fire-and-forget
        assertDoesNotThrow(() -> auditService.publish(event));
    }

    @Test
    void auditEventFactoryMethodsSetCorrectStatus() {
        UUID runId = UUID.randomUUID();

        AuditEvent attempted = AuditEvent.attempted(runId, "jira_get_issue", "{}");
        assertEquals(AuditEvent.Status.ATTEMPTED, attempted.status());

        AuditEvent success = AuditEvent.success(runId, "jira_get_issue", "result data");
        assertEquals(AuditEvent.Status.SUCCESS, success.status());

        AuditEvent failed = AuditEvent.failed(runId, "jira_get_issue", "timeout");
        assertEquals(AuditEvent.Status.FAILED, failed.status());

        AuditEvent blocked = AuditEvent.blocked(runId, "jira_delete_issue");
        assertEquals(AuditEvent.Status.BLOCKED, blocked.status());
    }

    @Test
    void auditEventTruncatesLongResults() {
        UUID runId = UUID.randomUUID();
        String longResult = "x".repeat(1000);

        AuditEvent event = AuditEvent.success(runId, "jira_get_issue", longResult);

        assertTrue(event.detail().length() <= 503); // 500 + "..."
        assertTrue(event.detail().endsWith("..."));
    }
}
