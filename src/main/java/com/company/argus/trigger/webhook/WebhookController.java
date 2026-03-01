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
public class WebhookController {

    private final WebhookAuthFilter webhookAuthFilter;
    private final PromptInjectionSanitizer sanitizer;
    private final QueuePort queuePort;

    public WebhookController(
            WebhookAuthFilter webhookAuthFilter,
            PromptInjectionSanitizer sanitizer,
            QueuePort queuePort) {
        this.webhookAuthFilter = webhookAuthFilter;
        this.sanitizer = sanitizer;
        this.queuePort = queuePort;
    }

    @PostMapping("/webhook/{agentType}")
    public ResponseEntity<Void> handle(
            @PathVariable AgentType agentType,
            @RequestBody String rawPayload,
            @RequestHeader("X-Argus-Signature") String sig,
            @RequestHeader("X-Argus-Timestamp") String ts) {

        webhookAuthFilter.verify(rawPayload, sig, ts);
        String sanitized = sanitizer.sanitize(rawPayload, DataSource.WEBHOOK_PAYLOAD);
        queuePort.publish(QueueNames.TRIGGER, new TriggerEvent(agentType, sanitized));
        return ResponseEntity.accepted().build();
    }
}
