package com.company.argus.agent;

import com.company.argus.AgentRunContext;
import com.company.argus.AgentType;
import com.company.argus.agent.impl.AlertNoiseAgent;
import com.company.argus.agent.impl.CsTriageAgent;
import com.company.argus.agent.impl.VersionDriftAgent;
import com.company.argus.audit.AuditEvent;
import com.company.argus.audit.AuditService;
import com.company.argus.queue.QueueNames;
import com.company.argus.queue.QueuePort;
import com.company.argus.TriggerEvent;
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
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public AgentOrchestrator(
            QueuePort queuePort,
            CsTriageAgent csTriageAgent,
            VersionDriftAgent versionDriftAgent,
            AlertNoiseAgent alertNoiseAgent,
            AuditService auditService,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        this.queuePort = queuePort;
        this.csTriageAgent = csTriageAgent;
        this.versionDriftAgent = versionDriftAgent;
        this.alertNoiseAgent = alertNoiseAgent;
        this.auditService = auditService;
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
        AgentRunContext.set(new AgentRunContext(agentRunId, agentType));

        try {
            String result = runAgent(agentType, event.payload());

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
            AgentRunContext.clear();
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
