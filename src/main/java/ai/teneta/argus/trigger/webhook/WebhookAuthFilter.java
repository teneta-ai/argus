package ai.teneta.argus.trigger.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class WebhookAuthFilter {

    private final String hmacSecret;
    private final long timestampToleranceSeconds;

    public WebhookAuthFilter(
            @Value("${argus.webhook.hmac-secret}") String hmacSecret,
            @Value("${argus.webhook.timestamp-tolerance-seconds:300}") long timestampToleranceSeconds) {
        this.hmacSecret = hmacSecret;
        this.timestampToleranceSeconds = timestampToleranceSeconds;
    }

    public void verify(String rawBody, String signature, String timestamp) {
        if (signature == null || timestamp == null) {
            throw new WebhookAuthException("Missing signature or timestamp headers");
        }

        // Replay protection
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new WebhookAuthException("Invalid timestamp format");
        }

        if (Math.abs(Instant.now().getEpochSecond() - ts) > timestampToleranceSeconds) {
            throw new WebhookAuthException("Timestamp out of tolerance window");
        }

        // HMAC-SHA256(secret, timestamp + "." + body)
        String baseString = timestamp + "." + rawBody;
        String expected = hmacSha256Hex(hmacSecret, baseString);
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
