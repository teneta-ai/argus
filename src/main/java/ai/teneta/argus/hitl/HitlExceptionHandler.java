package ai.teneta.argus.hitl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@ConditionalOnProperty(name = "argus.hitl.slack.bot-token")
public class HitlExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(HitlExceptionHandler.class);

    @ExceptionHandler(SlackVerificationException.class)
    public ResponseEntity<Void> handleSlackVerification(SlackVerificationException e) {
        log.warn("Slack verification failed: {}", e.getMessage());
        return ResponseEntity.status(401).build();
    }
}
