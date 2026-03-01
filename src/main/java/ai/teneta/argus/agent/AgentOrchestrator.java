package ai.teneta.argus.agent;

import ai.teneta.argus.shared.AgentType;
import ai.teneta.argus.shared.RunContextHolder;
import ai.teneta.argus.agent.impl.AlertNoiseAgent;
import ai.teneta.argus.agent.impl.CsTriageAgent;
import ai.teneta.argus.agent.impl.VersionDriftAgent;
import ai.teneta.argus.audit.AuditEvent;
import ai.teneta.argus.audit.AuditService;
import ai.teneta.argus.queue.QueueNames;
import ai.teneta.argus.queue.QueuePort;
import ai.teneta.argus.security.LlmOutputValidator;
import ai.teneta.argus.trigger.TriggerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final QueuePort queuePort;
    private final CsTriageAgent csTriageAgent;
    private final VersionDriftAgent versionDriftAgent;
    private final AlertNoiseAgent alertNoiseAgent;
    private final AuditService auditService;
    private final LlmOutputValidator llmOutputValidator;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public AgentOrchestrator(
            QueuePort queuePort,
            CsTriageAgent csTriageAgent,
            VersionDriftAgent versionDriftAgent,
            AlertNoiseAgent alertNoiseAgent,
            AuditService auditService,
            LlmOutputValidator llmOutputValidator,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        this.queuePort = queuePort;
        this.csTriageAgent = csTriageAgent;
        this.versionDriftAgent = versionDriftAgent;
        this.alertNoiseAgent = alertNoiseAgent;
        this.auditService = auditService;
        this.llmOutputValidator = llmOutputValidator;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void startListening() {
        queuePort.subscribe(QueueNames.TRIGGER, this::handleTrigger);
        log.info("AgentOrchestrator subscribed to {}", QueueNames.TRIGGER);
    }

    private void handleTrigger(String body) throws Exception {
        TriggerEvent event = objectMapper.readValue(body, TriggerEvent.class);
        UUID agentRunId = UUID.randomUUID();
        AgentType agentType = event.agentType();

        log.info("Agent run started: agentRunId={}, agentType={}", agentRunId, agentType);
        RunContextHolder.set(new AgentRunContext(agentRunId, agentType));

        try {
            String result = runAgent(agentType, event.payload());

            LlmOutputValidator.ValidationResult validation =
                    llmOutputValidator.validate(result, null);
            if (!validation.accepted()) {
                throw new IllegalStateException("LLM output rejected: " + validation.reason());
            }

            log.info("Agent run completed: agentRunId={}, agentType={}", agentRunId, agentType);
            auditService.publish(new AuditEvent(
                    agentRunId, "ORCHESTRATOR", AuditEvent.Status.SUCCESS,
                    "Agent completed: " + agentType, Instant.now()));

            eventPublisher.publishEvent(new AgentCompletedEvent(
                    agentRunId, agentType, result, Instant.now()));
        } catch (Exception e) {
            log.error("Agent run failed: agentRunId={}, agentType={}, error={}",
                    agentRunId, agentType, e.getMessage(), e);
            auditService.publish(new AuditEvent(
                    agentRunId, "ORCHESTRATOR", AuditEvent.Status.FAILED,
                    e.getMessage(), Instant.now()));
            throw e;
        } finally {
            RunContextHolder.clear();
        }
    }

    private String runAgent(AgentType agentType, String payload) {
        return switch (agentType) {
            case CS_TRIAGE -> csTriageAgent.triage(payload);
            case VERSION_DRIFT -> versionDriftAgent.detectDrift(payload);
            case ALERT_NOISE -> alertNoiseAgent.evaluateAlert(payload);
        };
    }
}
