package ai.teneta.argus.agent;

import ai.teneta.argus.agent.impl.AlertNoiseAgent;
import ai.teneta.argus.agent.impl.CsTriageAgent;
import ai.teneta.argus.agent.impl.VersionDriftAgent;
import ai.teneta.argus.audit.AuditEvent;
import ai.teneta.argus.audit.AuditService;
import ai.teneta.argus.queue.QueueNames;
import ai.teneta.argus.queue.QueuePort;
import ai.teneta.argus.shared.AgentType;
import ai.teneta.argus.shared.RunContextHolder;
import ai.teneta.argus.trigger.TriggerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentOrchestratorTest {

    private QueuePort queuePort;
    private CsTriageAgent csTriageAgent;
    private VersionDriftAgent versionDriftAgent;
    private AlertNoiseAgent alertNoiseAgent;
    private AuditService auditService;
    private ObjectMapper objectMapper;
    private ApplicationEventPublisher eventPublisher;
    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        queuePort = mock(QueuePort.class);
        csTriageAgent = mock(CsTriageAgent.class);
        versionDriftAgent = mock(VersionDriftAgent.class);
        alertNoiseAgent = mock(AlertNoiseAgent.class);
        auditService = mock(AuditService.class);
        objectMapper = new ObjectMapper();
        eventPublisher = mock(ApplicationEventPublisher.class);

        orchestrator = new AgentOrchestrator(
                queuePort, csTriageAgent, versionDriftAgent, alertNoiseAgent,
                auditService, objectMapper, eventPublisher);
    }

    @Test
    void startListeningSubscribesToTriggerQueue() {
        orchestrator.startListening();

        verify(queuePort).subscribe(eq(QueueNames.TRIGGER), any(QueuePort.MessageHandler.class));
    }

    @Test
    void dispatchesCsTriageAgentAndPublishesCompletionEvent() throws Exception {
        when(csTriageAgent.triage("PROJ-123")).thenReturn("Triaged successfully");

        // Capture the handler registered by startListening
        ArgumentCaptor<QueuePort.MessageHandler> handlerCaptor =
                ArgumentCaptor.forClass(QueuePort.MessageHandler.class);
        orchestrator.startListening();
        verify(queuePort).subscribe(eq(QueueNames.TRIGGER), handlerCaptor.capture());

        // Simulate a trigger message arriving
        TriggerEvent event = new TriggerEvent(AgentType.CS_TRIAGE, "PROJ-123");
        String json = objectMapper.writeValueAsString(event);
        handlerCaptor.getValue().handle(json);

        verify(csTriageAgent).triage("PROJ-123");
        verify(auditService).publish(argThat(e ->
                e.status() == AuditEvent.Status.SUCCESS
                        && e.toolName().equals("ORCHESTRATOR")));
        verify(eventPublisher).publishEvent(any(AgentCompletedEvent.class));
    }

    @Test
    void dispatchesVersionDriftAgent() throws Exception {
        when(versionDriftAgent.detectDrift("my-service:1.2.3")).thenReturn("No drift");

        ArgumentCaptor<QueuePort.MessageHandler> handlerCaptor =
                ArgumentCaptor.forClass(QueuePort.MessageHandler.class);
        orchestrator.startListening();
        verify(queuePort).subscribe(eq(QueueNames.TRIGGER), handlerCaptor.capture());

        TriggerEvent event = new TriggerEvent(AgentType.VERSION_DRIFT, "my-service:1.2.3");
        handlerCaptor.getValue().handle(objectMapper.writeValueAsString(event));

        verify(versionDriftAgent).detectDrift("my-service:1.2.3");
    }

    @Test
    void dispatchesAlertNoiseAgent() throws Exception {
        when(alertNoiseAgent.evaluateAlert("alert-group-42")).thenReturn("Expected noise");

        ArgumentCaptor<QueuePort.MessageHandler> handlerCaptor =
                ArgumentCaptor.forClass(QueuePort.MessageHandler.class);
        orchestrator.startListening();
        verify(queuePort).subscribe(eq(QueueNames.TRIGGER), handlerCaptor.capture());

        TriggerEvent event = new TriggerEvent(AgentType.ALERT_NOISE, "alert-group-42");
        handlerCaptor.getValue().handle(objectMapper.writeValueAsString(event));

        verify(alertNoiseAgent).evaluateAlert("alert-group-42");
    }

    @Test
    void agentFailurePublishesFailedAuditEvent() throws Exception {
        when(csTriageAgent.triage(any())).thenThrow(new RuntimeException("LLM timeout"));

        ArgumentCaptor<QueuePort.MessageHandler> handlerCaptor =
                ArgumentCaptor.forClass(QueuePort.MessageHandler.class);
        orchestrator.startListening();
        verify(queuePort).subscribe(eq(QueueNames.TRIGGER), handlerCaptor.capture());

        TriggerEvent event = new TriggerEvent(AgentType.CS_TRIAGE, "PROJ-999");
        String json = objectMapper.writeValueAsString(event);

        assertThrows(Exception.class, () -> handlerCaptor.getValue().handle(json));

        verify(auditService).publish(argThat(e ->
                e.status() == AuditEvent.Status.FAILED
                        && e.detail().contains("LLM timeout")));
        verify(eventPublisher, never()).publishEvent(any(AgentCompletedEvent.class));
    }

    @Test
    void runContextIsClearedAfterSuccessfulRun() throws Exception {
        when(csTriageAgent.triage(any())).thenReturn("done");

        ArgumentCaptor<QueuePort.MessageHandler> handlerCaptor =
                ArgumentCaptor.forClass(QueuePort.MessageHandler.class);
        orchestrator.startListening();
        verify(queuePort).subscribe(eq(QueueNames.TRIGGER), handlerCaptor.capture());

        TriggerEvent event = new TriggerEvent(AgentType.CS_TRIAGE, "PROJ-1");
        handlerCaptor.getValue().handle(objectMapper.writeValueAsString(event));

        assertThrows(IllegalStateException.class, RunContextHolder::current,
                "RunContext should be cleared after agent run");
    }

    @Test
    void runContextIsClearedAfterFailedRun() throws Exception {
        when(csTriageAgent.triage(any())).thenThrow(new RuntimeException("boom"));

        ArgumentCaptor<QueuePort.MessageHandler> handlerCaptor =
                ArgumentCaptor.forClass(QueuePort.MessageHandler.class);
        orchestrator.startListening();
        verify(queuePort).subscribe(eq(QueueNames.TRIGGER), handlerCaptor.capture());

        TriggerEvent event = new TriggerEvent(AgentType.CS_TRIAGE, "PROJ-1");
        try {
            handlerCaptor.getValue().handle(objectMapper.writeValueAsString(event));
        } catch (Exception ignored) {
        }

        assertThrows(IllegalStateException.class, RunContextHolder::current,
                "RunContext should be cleared even after failure");
    }
}
