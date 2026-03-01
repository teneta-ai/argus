package ai.teneta.argus.trigger.webhook;

import ai.teneta.argus.shared.AgentType;
import ai.teneta.argus.queue.QueuePort;
import ai.teneta.argus.tool.sanitizer.DataSource;
import ai.teneta.argus.tool.sanitizer.PromptInjectionSanitizer;
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
        queuePort.publish(agentType.queueName(), sanitized);
        return ResponseEntity.accepted().build();
    }
}
