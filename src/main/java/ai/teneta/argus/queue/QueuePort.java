package ai.teneta.argus.queue;

public interface QueuePort {

    void publish(String queueName, Object payload);

    void subscribe(String queueName, MessageHandler handler);

    @FunctionalInterface
    interface MessageHandler {
        void handle(String body) throws Exception;
    }
}
