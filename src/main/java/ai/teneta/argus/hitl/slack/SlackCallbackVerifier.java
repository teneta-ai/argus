package ai.teneta.argus.hitl.slack;

import ai.teneta.argus.hitl.SlackVerificationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Component
@ConditionalOnProperty(name = "argus.hitl.channel", havingValue = "slack")
public class SlackCallbackVerifier {

    private final String signingSecret;

    public SlackCallbackVerifier(@Value("${argus.hitl.slack.signing-secret}") String signingSecret) {
        this.signingSecret = signingSecret;
    }

    public void verify(HttpServletRequest request, String rawBody) {
        String timestamp = request.getHeader("X-Slack-Request-Timestamp");
        String signature = request.getHeader("X-Slack-Signature");

        if (timestamp == null || signature == null) {
            throw new SlackVerificationException("Missing Slack signature headers");
        }

        // Replay protection — reject if timestamp > 5 min old
        long ts = Long.parseLong(timestamp);
        if (Math.abs(Instant.now().getEpochSecond() - ts) > 300) {
            throw new SlackVerificationException("Timestamp out of window");
        }

        String baseString = "v0:" + timestamp + ":" + rawBody;
        String expected = "v0=" + hmacSha256(signingSecret, baseString);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new SlackVerificationException("HMAC mismatch");
        }
    }

    private static String hmacSha256(String key, String data) {
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
