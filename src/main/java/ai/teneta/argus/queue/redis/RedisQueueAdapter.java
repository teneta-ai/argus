package ai.teneta.argus.queue.redis;

import ai.teneta.argus.queue.QueuePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RedisQueueAdapter implements QueuePort {

    private static final Logger log = LoggerFactory.getLogger(RedisQueueAdapter.class);
    private static final Duration POP_TIMEOUT = Duration.ofSeconds(2);
    private static final int MAX_RETRIES = 3;
    private static final String DEAD_LETTER_SUFFIX = ":dlq";
    private static final String RETRY_SUFFIX = ":retries";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Map<String, Thread> pollingThreads = new ConcurrentHashMap<>();

    public RedisQueueAdapter(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String queueName, Object payload) {
        String body;
        if (payload instanceof String s) {
            body = s;
        } else {
            try {
                body = objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Failed to serialize queue payload", e);
            }
        }

        redis.opsForList().leftPush(queueName, body);
        log.debug("Published message to queue={}", queueName);
    }

    @Override
    public void subscribe(String queueName, MessageHandler handler) {
        Thread pollingThread = Thread.ofVirtual().name("redis-queue-" + queueName).start(() -> {
            log.info("Starting Redis queue poll loop for queue={}", queueName);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String message = redis.opsForList().rightPop(queueName, POP_TIMEOUT);
                    if (message == null) {
                        continue;
                    }

                    String retryKey = sha256Hex(message);
                    try {
                        handler.handle(message);
                        redis.opsForHash().delete(queueName + RETRY_SUFFIX, retryKey);
                    } catch (Exception e) {
                        log.error("Failed to handle message from queue={}: {}",
                                queueName, e.getMessage(), e);

                        Long retries = redis.opsForHash().increment(
                                queueName + RETRY_SUFFIX, retryKey, 1);
                        if (retries <= MAX_RETRIES) {
                            log.warn("Re-enqueuing message for retry {}/{} on queue={}",
                                    retries, MAX_RETRIES, queueName);
                            redis.opsForList().leftPush(queueName, message);
                        } else {
                            log.error("Message exceeded {} retries, moving to DLQ: {}",
                                    MAX_RETRIES, queueName + DEAD_LETTER_SUFFIX);
                            redis.opsForList().leftPush(queueName + DEAD_LETTER_SUFFIX, message);
                            redis.opsForHash().delete(queueName + RETRY_SUFFIX, retryKey);
                        }
                    }
                } catch (Exception e) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    log.error("Error polling queue={}: {}", queueName, e.getMessage(), e);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.info("Stopped Redis queue poll loop for queue={}", queueName);
        });

        pollingThreads.put(queueName, pollingThread);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
