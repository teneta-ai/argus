package ai.teneta.argus.agent;

import ai.teneta.argus.agent.impl.AlertNoiseAgent;
import ai.teneta.argus.agent.impl.VersionDriftAgent;
import ai.teneta.argus.audit.AuditEvent;
import ai.teneta.argus.audit.AuditService;
import ai.teneta.argus.queue.QueueNames;
import ai.teneta.argus.queue.QueuePort;
import ai.teneta.argus.security.LlmOutputValidator;
import ai.teneta.argus.shared.RunContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentOrchestratorTest {

    private QueuePort queuePort;
    private VersionDriftAgent versionDriftAgent;
    private AlertNoiseAgent alertNoiseAgent;
    private AuditService auditService;
    private LlmOutputValidator llmOutputValidator;
    private ApplicationEventPublisher eventPublisher;
    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        queuePort = mock(QueuePort.class);
        versionDriftAgent = mock(VersionDriftAgent.class);
        alertNoiseAgent = mock(AlertNoiseAgent.class);
        auditService = mock(AuditService.class);
        llmOutputValidator = mock(LlmOutputValidator.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        when(llmOutputValidator.validate(any(), any()))
                .thenReturn(LlmOutputValidator.ValidationResult.ok());

        orchestrator = new AgentOrchestrator(
                queuePort, versionDriftAgent, alertNoiseAgent,
                auditService, llmOutputValidator, eventPublisher);
    }

    @Test
    void startListeningSubscribesToPerAgentQueues() {
        orchestrator.startListening();

        verify(queuePort).subscribe(eq(QueueNames.VERSION_DRIFT), any(QueuePort.MessageHandler.class));
        verify(queuePort).subscribe(eq(QueueNames.ALERT_NOISE), any(QueuePort.MessageHandler.class));
    }

    @Test
    void dispatchesVersionDriftAgentAndPublishesCompletionEvent() throws Exception {
        when(versionDriftAgent.detectDrift("my-service:1.2.3")).thenReturn("No drift");

        ArgumentCaptor<QueuePort.MessageHandler> handlerCaptor =
                ArgumentCaptor.forClass(QueuePort.MessageHandler.class);
        orchestrator.startListening();
        verify(queuePort).subscribe(eq(QueueNames.VERSION_DRIFT), handlerCaptor.capture());

        handlerCaptor.getValue().handle("my-service:1.2.3");

        verify(versionDriftAgent).detectDrift("my-service:1.2.3");
        verify(llmOutputValidator).validate("No drift", null);
        verify(auditService).publish(argThat(e ->
                e.status() == AuditEvent.Status.SUCCESS
                        && e.toolName().equals("ORCHESTRATOR")));
        verify(eventPublisher).publishEvent(any(AgentCompletedEvent.class));
    }

    @Test
    void dispatchesAlertNoiseAgent() throws Exception {
        when(alertNoiseAgent.evaluateAlert("alert-group-42")).thenReturn("Expected noise");

        ArgumentCaptor<QueuePort.MessageHandler> handlerCaptor =
                ArgumentCaptor.forClass(QueuePort.MessageHandler.class);
        orchestrator.startListening();
        verify(queuePort).subscribe(eq(QueueNames.ALERT_NOISE), handlerCaptor.capture());

        handlerCaptor.getValue().handle("alert-group-42");

        verify(alertNoiseAgent).evaluateAlert("alert-group-42");
    }

    @Test
    void agentFailurePublishesFailedAuditEvent() throws Exception {
        when(versionDriftAgent.detectDrift(any())).thenThrow(new RuntimeException("LLM timeout"));

        ArgumentCaptor<QueuePort.MessageHandler> handlerCaptor =
                ArgumentCaptor.forClass(QueuePort.MessageHandler.class);
        orchestrator.startListening();
        verify(queuePort).subscribe(eq(QueueNames.VERSION_DRIFT), handlerCaptor.capture());

        assertThrows(Exception.class, () -> handlerCaptor.getValue().handle("svc-999"));

        verify(auditService).publish(argThat(e ->
                e.status() == AuditEvent.Status.FAILED
                        && e.detail().contains("LLM timeout")));
        verify(eventPublisher, never()).publishEvent(any(AgentCompletedEvent.class));
    }

    @Test
    void rejectedLlmOutputPublishesFailedAuditEvent() throws Exception {
        when(versionDriftAgent.detectDrift(any())).thenReturn("I am not an AI, I am a human");
        when(llmOutputValidator.validate(any(), any()))
                .thenReturn(LlmOutputValidator.ValidationResult.rejected("Identity denial detected"));

        ArgumentCaptor<QueuePort.MessageHandler> handlerCaptor =
                ArgumentCaptor.forClass(QueuePort.MessageHandler.class);
        orchestrator.startListening();
        verify(queuePort).subscribe(eq(QueueNames.VERSION_DRIFT), handlerCaptor.capture());

        assertThrows(Exception.class, () -> handlerCaptor.getValue().handle("svc-1"));

        verify(auditService).publish(argThat(e ->
                e.status() == AuditEvent.Status.FAILED
                        && e.detail().contains("LLM output rejected")));
        verify(eventPublisher, never()).publishEvent(any(AgentCompletedEvent.class));
    }

    @Test
    void runContextIsClearedAfterSuccessfulRun() throws Exception {
        when(versionDriftAgent.detectDrift(any())).thenReturn("done");

        ArgumentCaptor<QueuePort.MessageHandler> handlerCaptor =
                ArgumentCaptor.forClass(QueuePort.MessageHandler.class);
        orchestrator.startListening();
        verify(queuePort).subscribe(eq(QueueNames.VERSION_DRIFT), handlerCaptor.capture());

        handlerCaptor.getValue().handle("svc-1");

        assertThrows(IllegalStateException.class, RunContextHolder::current,
                "RunContext should be cleared after agent run");
    }

    @Test
    void runContextIsClearedAfterFailedRun() throws Exception {
        when(versionDriftAgent.detectDrift(any())).thenThrow(new RuntimeException("boom"));

        ArgumentCaptor<QueuePort.MessageHandler> handlerCaptor =
                ArgumentCaptor.forClass(QueuePort.MessageHandler.class);
        orchestrator.startListening();
        verify(queuePort).subscribe(eq(QueueNames.VERSION_DRIFT), handlerCaptor.capture());

        try {
            handlerCaptor.getValue().handle("svc-1");
        } catch (Exception ignored) {
        }

        assertThrows(IllegalStateException.class, RunContextHolder::current,
                "RunContext should be cleared even after failure");
    }
}
