package ai.teneta.argus.queue;

public record QueueMessage(String receiptHandle, String body) {
}
