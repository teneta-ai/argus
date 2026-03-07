package ai.teneta.argus.audit;

import ai.teneta.argus.queue.QueueNames;
import ai.teneta.argus.queue.QueuePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final QueuePort queuePort;

    public AuditService(QueuePort queuePort) {
        this.queuePort = queuePort;
    }

    public void publish(AuditEvent event) {
        try {
            queuePort.publish(QueueNames.AUDIT, event);
            log.debug("Audit event published: agentRunId={}, tool={}, status={}",
                    event.agentRunId(), event.toolName(), event.status());
        } catch (Exception e) {
            // Fire-and-forget — do not let audit failures break the agent pipeline
            log.error("Failed to publish audit event: {}", e.getMessage(), e);
        }
    }
}
