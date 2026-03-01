package ai.teneta.argus.trigger.webhook;

public class WebhookAuthException extends RuntimeException {

    public WebhookAuthException(String message) {
        super(message);
    }
}
