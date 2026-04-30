package ai.teneta.argus.agent;

import ai.teneta.argus.shared.AgentType;
import ai.teneta.argus.shared.RunContextHolder;
import ai.teneta.argus.agent.impl.AlertNoiseAgent;
import ai.teneta.argus.agent.impl.VersionDriftAgent;
import ai.teneta.argus.audit.AuditEvent;
import ai.teneta.argus.audit.AuditService;
import ai.teneta.argus.queue.QueueNames;
import ai.teneta.argus.queue.QueuePort;
import ai.teneta.argus.security.LlmOutputValidator;
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
    private final VersionDriftAgent versionDriftAgent;
    private final AlertNoiseAgent alertNoiseAgent;
    private final AuditService auditService;
    private final LlmOutputValidator llmOutputValidator;
    private final ApplicationEventPublisher eventPublisher;

    public AgentOrchestrator(
            QueuePort queuePort,
            VersionDriftAgent versionDriftAgent,
            AlertNoiseAgent alertNoiseAgent,
            AuditService auditService,
            LlmOutputValidator llmOutputValidator,
            ApplicationEventPublisher eventPublisher) {
        this.queuePort = queuePort;
        this.versionDriftAgent = versionDriftAgent;
        this.alertNoiseAgent = alertNoiseAgent;
        this.auditService = auditService;
        this.llmOutputValidator = llmOutputValidator;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void startListening() {
        queuePort.subscribe(QueueNames.VERSION_DRIFT, payload -> handleAgent(AgentType.VERSION_DRIFT, payload));
        queuePort.subscribe(QueueNames.ALERT_NOISE, payload -> handleAgent(AgentType.ALERT_NOISE, payload));
        log.info("AgentOrchestrator subscribed to per-agent queues");
    }

    private void handleAgent(AgentType agentType, String payload) throws Exception {
        UUID agentRunId = UUID.randomUUID();

        log.info("Agent run started: agentRunId={}, agentType={}", agentRunId, agentType);
        RunContextHolder.set(new AgentRunContext(agentRunId, agentType));

        try {
            String result = runAgent(agentType, payload);

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
            case VERSION_DRIFT -> versionDriftAgent.detectDrift(payload);
            case ALERT_NOISE -> alertNoiseAgent.evaluateAlert(payload);
        };
    }
}
