package ai.teneta.argus.hitl.slack;

import ai.teneta.argus.hitl.ApprovalRequest;
import ai.teneta.argus.hitl.ApprovalStatus;
import ai.teneta.argus.hitl.HitlNotificationChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "argus.hitl.channel", havingValue = "slack")
public class SlackHitlNotificationChannel implements HitlNotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SlackHitlNotificationChannel.class);
    private static final String SLACK_POST_MESSAGE_URL = "https://slack.com/api/chat.postMessage";
    private static final String SLACK_UPDATE_URL = "https://slack.com/api/chat.update";

    private final String channelId;
    private final String botToken;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SlackHitlNotificationChannel(
            @Value("${argus.hitl.slack.channel-id}") String channelId,
            @Value("${argus.hitl.slack.bot-token}") String botToken,
            ObjectMapper objectMapper) {
        this.channelId = channelId;
        this.botToken = botToken;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void sendApprovalRequest(ApprovalRequest request) {
        try {
            Map<String, Object> payload = Map.of(
                    "channel", channelId,
                    "text", "HITL Approval Required: " + request.toolName()
                            + " (Agent run: " + request.agentRunId() + ")",
                    "blocks", buildApprovalBlocks(request)
            );

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(SLACK_POST_MESSAGE_URL))
                    .header("Authorization", "Bearer " + botToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            log.info("Sent HITL approval request to Slack: agentRunId={}", request.agentRunId());
        } catch (IOException | InterruptedException e) {
            log.error("Failed to send Slack approval request: {}", e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void updateWithDecision(String correlationId, ApprovalStatus status, String decidedBy) {
        log.info("HITL decision for {}: {} by {}", correlationId, status,
                decidedBy != null ? decidedBy : "system");
    }

    @Override
    public String channelName() {
        return "slack";
    }

    private Object buildApprovalBlocks(ApprovalRequest request) {
        return java.util.List.of(
                Map.of("type", "section", "text",
                        Map.of("type", "mrkdwn", "text",
                                "*HITL Approval Required*\n"
                                        + "Tool: `" + request.toolName() + "`\n"
                                        + "Agent Run: `" + request.agentRunId() + "`\n"
                                        + "Expires: " + request.expiresAt())),
                Map.of("type", "actions", "elements", java.util.List.of(
                        Map.of("type", "button", "text",
                                Map.of("type", "plain_text", "text", "Approve"),
                                "style", "primary",
                                "action_id", "hitl_approve",
                                "value", request.agentRunId().toString()),
                        Map.of("type", "button", "text",
                                Map.of("type", "plain_text", "text", "Reject"),
                                "style", "danger",
                                "action_id", "hitl_reject",
                                "value", request.agentRunId().toString())
                ))
        );
    }
}
