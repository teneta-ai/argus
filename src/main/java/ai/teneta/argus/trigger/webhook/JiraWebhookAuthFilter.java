package ai.teneta.argus.trigger.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class JiraWebhookAuthFilter {

    private final String secret;

    public JiraWebhookAuthFilter(@Value("${argus.webhook.jira.secret}") String secret) {
        this.secret = secret;
    }

    public void verify(String rawBody, String signature) {
        if (signature == null || !signature.startsWith("sha256=")) {
            throw new WebhookAuthException("Missing or malformed X-Hub-Signature");
        }

        String expected = "sha256=" + WebhookAuthFilter.hmacSha256Hex(secret, rawBody);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new WebhookAuthException("HMAC mismatch");
        }
    }
}
