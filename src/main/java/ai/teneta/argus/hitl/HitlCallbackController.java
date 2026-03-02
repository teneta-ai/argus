package ai.teneta.argus.hitl;

import ai.teneta.argus.hitl.slack.SlackCallbackVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "argus.hitl.slack.bot-token")
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

    @PostMapping(value = "/hitl/slack/interaction",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> handleInteraction(
            HttpServletRequest request,
            @RequestBody String rawBody) {

        // Verify HMAC over the original raw body before any decoding
        slackCallbackVerifier.verify(request, rawBody);

        try {
            // Slack sends application/x-www-form-urlencoded with a "payload" parameter
            String jsonPayload = extractPayloadParam(rawBody);
            JsonNode payload = objectMapper.readTree(jsonPayload);
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

    private String extractPayloadParam(String formBody) {
        for (String param : formBody.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "payload".equals(kv[0])) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Missing 'payload' parameter in Slack interaction request");
    }
}
