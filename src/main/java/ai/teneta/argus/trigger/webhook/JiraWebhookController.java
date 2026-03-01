package ai.teneta.argus.trigger.webhook;

import ai.teneta.argus.shared.AgentType;
import ai.teneta.argus.queue.QueueNames;
import ai.teneta.argus.queue.QueuePort;
import ai.teneta.argus.tool.sanitizer.DataSource;
import ai.teneta.argus.tool.sanitizer.PromptInjectionSanitizer;
import ai.teneta.argus.trigger.TriggerEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class JiraWebhookController {

    private final JiraWebhookAuthFilter jiraWebhookAuthFilter;
    private final WebhookDeduplicator deduplicator;
    private final PromptInjectionSanitizer sanitizer;
    private final QueuePort queuePort;

    public JiraWebhookController(
            JiraWebhookAuthFilter jiraWebhookAuthFilter,
            WebhookDeduplicator deduplicator,
            PromptInjectionSanitizer sanitizer,
            QueuePort queuePort) {
        this.jiraWebhookAuthFilter = jiraWebhookAuthFilter;
        this.deduplicator = deduplicator;
        this.sanitizer = sanitizer;
        this.queuePort = queuePort;
    }

    @PostMapping("/webhook/jira/{agentType}")
    public ResponseEntity<Void> handle(
            @PathVariable AgentType agentType,
            @RequestBody String rawPayload,
            @RequestHeader("X-Hub-Signature") String signature,
            @RequestHeader("X-Atlassian-Webhook-Identifier") String webhookId) {

        jiraWebhookAuthFilter.verify(rawPayload, signature);

        // Deduplicate retries — Jira retries up to 5x on failure
        if (deduplicator.isDuplicate(webhookId)) {
            return ResponseEntity.ok().build();
        }

        String sanitized = sanitizer.sanitize(rawPayload, DataSource.WEBHOOK_PAYLOAD);
        queuePort.publish(QueueNames.TRIGGER, new TriggerEvent(agentType, sanitized));
        return ResponseEntity.accepted().build();
    }
}
