package ai.teneta.argus.trigger.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

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

        String expected = "sha256=" + hmacSha256Hex(secret, rawBody);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new WebhookAuthException("HMAC mismatch");
        }
    }

    static String hmacSha256Hex(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }
}
