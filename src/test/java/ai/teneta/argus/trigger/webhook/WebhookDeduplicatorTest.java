package ai.teneta.argus.trigger.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebhookDeduplicatorTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private WebhookDeduplicator deduplicator;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        deduplicator = new WebhookDeduplicator(redis, 3600);
    }

    @Test
    void firstCallIsNotDuplicate() {
        when(valueOps.setIfAbsent("webhook:seen:wh-001", "1", 3600, TimeUnit.SECONDS))
                .thenReturn(Boolean.TRUE);

        assertFalse(deduplicator.isDuplicate("wh-001"));
    }

    @Test
    void secondCallIsDuplicate() {
        when(valueOps.setIfAbsent("webhook:seen:wh-001", "1", 3600, TimeUnit.SECONDS))
                .thenReturn(Boolean.FALSE);

        assertTrue(deduplicator.isDuplicate("wh-001"));
    }

    @Test
    void nullReturnFromRedisIsTreatedAsDuplicate() {
        when(valueOps.setIfAbsent("webhook:seen:wh-002", "1", 3600, TimeUnit.SECONDS))
                .thenReturn(null);

        assertTrue(deduplicator.isDuplicate("wh-002"),
                "Null from SETNX should be treated as duplicate (fail-safe)");
    }

    @Test
    void usesConfiguredTtl() {
        WebhookDeduplicator customTtl = new WebhookDeduplicator(redis, 120);
        when(valueOps.setIfAbsent("webhook:seen:wh-003", "1", 120, TimeUnit.SECONDS))
                .thenReturn(Boolean.TRUE);

        assertFalse(customTtl.isDuplicate("wh-003"));

        verify(valueOps).setIfAbsent("webhook:seen:wh-003", "1", 120, TimeUnit.SECONDS);
    }
}
