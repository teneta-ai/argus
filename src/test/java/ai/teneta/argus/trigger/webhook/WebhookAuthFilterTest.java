package ai.teneta.argus.trigger.webhook;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WebhookAuthFilterTest {

    private static final String SECRET = "test-secret";
    private final WebhookAuthFilter filter = new WebhookAuthFilter(SECRET, 300);

    @Test
    void validHmacPasses() {
        String body = "{\"issueKey\":\"CS-1234\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String baseString = timestamp + "." + body;
        String signature = WebhookAuthFilter.hmacSha256Hex(SECRET, baseString);

        assertDoesNotThrow(() -> filter.verify(body, signature, timestamp));
    }

    @Test
    void invalidHmacThrows() {
        String body = "{\"issueKey\":\"CS-1234\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        assertThrows(WebhookAuthException.class,
                () -> filter.verify(body, "badsignature", timestamp));
    }

    @Test
    void replayAttackThrows() {
        String body = "{\"issueKey\":\"CS-1234\"}";
        // Timestamp from 10 minutes ago (beyond 300s tolerance)
        String staleTimestamp = String.valueOf(Instant.now().getEpochSecond() - 601);
        String baseString = staleTimestamp + "." + body;
        String signature = WebhookAuthFilter.hmacSha256Hex(SECRET, baseString);

        WebhookAuthException ex = assertThrows(WebhookAuthException.class,
                () -> filter.verify(body, signature, staleTimestamp));
        assertTrue(ex.getMessage().contains("tolerance"));
    }

    @Test
    void missingHeadersThrow() {
        assertThrows(WebhookAuthException.class,
                () -> filter.verify("body", null, "123"));
        assertThrows(WebhookAuthException.class,
                () -> filter.verify("body", "sig", null));
    }
}
