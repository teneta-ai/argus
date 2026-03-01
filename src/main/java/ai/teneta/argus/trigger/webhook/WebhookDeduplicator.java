package ai.teneta.argus.trigger.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class WebhookDeduplicator {

    private final StringRedisTemplate redis;
    private final long ttlSeconds;

    public WebhookDeduplicator(
            StringRedisTemplate redis,
            @Value("${argus.webhook.deduplication-ttl-seconds:3600}") long ttlSeconds) {
        this.redis = redis;
        this.ttlSeconds = ttlSeconds;
    }

    public boolean isDuplicate(String webhookId) {
        Boolean inserted = redis.opsForValue()
                .setIfAbsent("webhook:seen:" + webhookId, "1", ttlSeconds, TimeUnit.SECONDS);
        return !Boolean.TRUE.equals(inserted);
    }
}
