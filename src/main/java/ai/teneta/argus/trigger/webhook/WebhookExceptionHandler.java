package ai.teneta.argus.trigger.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class WebhookExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebhookExceptionHandler.class);

    @ExceptionHandler(WebhookAuthException.class)
    public ResponseEntity<Void> handleWebhookAuth(WebhookAuthException e) {
        log.warn("Webhook auth failed: {}", e.getMessage());
        return ResponseEntity.status(401).build();
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Void> handleMissingHeader(MissingRequestHeaderException e) {
        log.warn("Missing required auth header: {}", e.getHeaderName());
        return ResponseEntity.status(401).build();
    }
}
