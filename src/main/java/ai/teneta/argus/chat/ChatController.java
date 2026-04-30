package ai.teneta.argus.chat;

import ai.teneta.argus.agent.AgentRunContext;
import ai.teneta.argus.agent.impl.AlertNoiseAgent;
import ai.teneta.argus.agent.impl.VersionDriftAgent;
import ai.teneta.argus.audit.AuditEvent;
import ai.teneta.argus.audit.AuditService;
import ai.teneta.argus.security.LlmOutputValidator;
import ai.teneta.argus.shared.AgentType;
import ai.teneta.argus.shared.RunContextHolder;
import ai.teneta.argus.tool.sanitizer.DataSource;
import ai.teneta.argus.tool.sanitizer.PromptInjectionSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@ConditionalOnProperty(name = "argus.chat.enabled", havingValue = "true")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final VersionDriftAgent versionDriftAgent;
    private final AlertNoiseAgent alertNoiseAgent;
    private final AuditService auditService;
    private final LlmOutputValidator llmOutputValidator;
    private final PromptInjectionSanitizer sanitizer;

    public ChatController(
            VersionDriftAgent versionDriftAgent,
            AlertNoiseAgent alertNoiseAgent,
            AuditService auditService,
            LlmOutputValidator llmOutputValidator,
            PromptInjectionSanitizer sanitizer) {
        this.versionDriftAgent = versionDriftAgent;
        this.alertNoiseAgent = alertNoiseAgent;
        this.auditService = auditService;
        this.llmOutputValidator = llmOutputValidator;
        this.sanitizer = sanitizer;
    }

    record ChatRequest(AgentType agentType, String message) {}
    record ChatResponse(UUID runId, AgentType agentType, String response) {}
    record ErrorResponse(String error) {}

    @PostMapping
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        if (request.agentType() == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Agent type is required"));
        }
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Message is required"));
        }

        UUID runId = UUID.randomUUID();
        AgentType agentType = request.agentType();
        log.info("Chat request: runId={}, agentType={}", runId, agentType);

        RunContextHolder.set(new AgentRunContext(runId, agentType));
        try {
            String sanitized = sanitizer.sanitize(request.message(), DataSource.WEBHOOK_PAYLOAD);
            String result = runAgent(agentType, sanitized);

            LlmOutputValidator.ValidationResult validation =
                    llmOutputValidator.validate(result, null);
            if (!validation.accepted()) {
                throw new IllegalStateException("LLM output rejected: " + validation.reason());
            }

            log.info("Chat completed: runId={}, agentType={}", runId, agentType);
            auditService.publish(new AuditEvent(
                    runId, "CHAT", AuditEvent.Status.SUCCESS,
                    "Chat completed: " + agentType, Instant.now()));

            return ResponseEntity.ok(new ChatResponse(runId, agentType, result));
        } catch (Exception e) {
            log.error("Chat failed: runId={}, agentType={}, error={}",
                    runId, agentType, e.getMessage(), e);
            auditService.publish(new AuditEvent(
                    runId, "CHAT", AuditEvent.Status.FAILED,
                    e.getMessage(), Instant.now()));
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("Agent failed: " + e.getMessage()));
        } finally {
            RunContextHolder.clear();
        }
    }

    @GetMapping("/agents")
    public ResponseEntity<?> listAgents() {
        record AgentInfo(String name, String description) {}
        var agents = java.util.Arrays.stream(AgentType.values())
                .map(a -> new AgentInfo(a.name(), a.description()))
                .toList();
        return ResponseEntity.ok(agents);
    }

    private String runAgent(AgentType agentType, String payload) {
        return switch (agentType) {
            case VERSION_DRIFT -> versionDriftAgent.detectDrift(payload);
            case ALERT_NOISE -> alertNoiseAgent.evaluateAlert(payload);
        };
    }
}
