package ai.teneta.argus.queue.sqs;

import ai.teneta.argus.queue.QueueNames;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloud.aws.queues")
public record SqsQueueProperties(
        String trigger,
        String hitlRequest,
        String audit
) {
    public String resolveUrl(String queueName) {
        return switch (queueName) {
            case QueueNames.TRIGGER -> trigger;
            case QueueNames.HITL_REQUEST -> hitlRequest;
            case QueueNames.AUDIT -> audit;
            default -> throw new IllegalArgumentException("Unknown queue: " + queueName);
        };
    }
}
