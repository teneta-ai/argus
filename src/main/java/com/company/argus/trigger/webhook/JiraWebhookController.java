package com.company.argus.trigger.webhook;

import com.company.argus.AgentType;
import com.company.argus.queue.QueueNames;
import com.company.argus.queue.QueuePort;
import com.company.argus.tool.sanitizer.DataSource;
import com.company.argus.tool.sanitizer.PromptInjectionSanitizer;
import com.company.argus.TriggerEvent;
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
