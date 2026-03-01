package ai.teneta.argus.hitl;

import ai.teneta.argus.hitl.slack.SlackCallbackVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "argus.hitl.channel", havingValue = "slack")
public class HitlCallbackController {

    private static final Logger log = LoggerFactory.getLogger(HitlCallbackController.class);

    private final SlackCallbackVerifier slackCallbackVerifier;
    private final HitlService hitlService;
    private final ObjectMapper objectMapper;

    public HitlCallbackController(
            SlackCallbackVerifier slackCallbackVerifier,
            HitlService hitlService,
            ObjectMapper objectMapper) {
        this.slackCallbackVerifier = slackCallbackVerifier;
        this.hitlService = hitlService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/hitl/slack/interaction")
    public ResponseEntity<Void> handleInteraction(
            HttpServletRequest request,
            @RequestBody String rawBody) {

        // Verify HMAC before any deserialization
        slackCallbackVerifier.verify(request, rawBody);

        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            JsonNode actions = payload.path("actions");
            if (actions.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            JsonNode action = actions.get(0);
            String actionId = action.path("action_id").asText();
            String correlationId = action.path("value").asText();
            String userName = payload.path("user").path("name").asText("unknown");

            ApprovalStatus decision = "hitl_approve".equals(actionId)
                    ? ApprovalStatus.APPROVED
                    : ApprovalStatus.REJECTED;

            UUID requestId = UUID.fromString(correlationId);
            hitlService.resolve(requestId, decision, userName);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to process Slack interaction: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
