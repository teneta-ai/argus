package ai.teneta.argus.queue.sqs;

import ai.teneta.argus.queue.QueuePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SqsQueueAdapter implements QueuePort {

    private static final Logger log = LoggerFactory.getLogger(SqsQueueAdapter.class);

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final SqsQueueProperties queueProperties;
    private final Map<String, Thread> pollingThreads = new ConcurrentHashMap<>();

    public SqsQueueAdapter(SqsClient sqsClient, ObjectMapper objectMapper, SqsQueueProperties queueProperties) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.queueProperties = queueProperties;
    }

    @Override
    public void publish(String queueName, Object payload) {
        String queueUrl = queueProperties.resolveUrl(queueName);
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

        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .build());

        log.debug("Published message to queue={}", queueName);
    }

    @Override
    public void subscribe(String queueName, MessageHandler handler) {
        String queueUrl = queueProperties.resolveUrl(queueName);

        Thread pollingThread = Thread.ofVirtual().name("sqs-poll-" + queueName).start(() -> {
            log.info("Starting SQS poll loop for queue={}", queueName);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(10)
                            .waitTimeSeconds(20)
                            .build()).messages();

                    for (Message message : messages) {
                        try {
                            handler.handle(message.body());
                            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                    .queueUrl(queueUrl)
                                    .receiptHandle(message.receiptHandle())
                                    .build());
                        } catch (Exception e) {
                            log.error("Failed to handle message from queue={}, messageId={}: {}",
                                    queueName, message.messageId(), e.getMessage(), e);
                            // Do NOT delete — message returns to queue / DLQ
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
            log.info("Stopped SQS poll loop for queue={}", queueName);
        });

        pollingThreads.put(queueName, pollingThread);
    }
}
