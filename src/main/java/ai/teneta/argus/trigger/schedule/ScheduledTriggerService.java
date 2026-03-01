package ai.teneta.argus.trigger.schedule;

import ai.teneta.argus.shared.AgentType;
import ai.teneta.argus.queue.QueuePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ScheduledTriggerService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTriggerService.class);

    private final QueuePort queuePort;

    public ScheduledTriggerService(QueuePort queuePort) {
        this.queuePort = queuePort;
    }

    @Scheduled(cron = "${argus.schedule.version-drift:0 0 8 * * MON-FRI}")
    public void triggerVersionDrift() {
        log.info("Scheduled trigger: VERSION_DRIFT");
        queuePort.publish(AgentType.VERSION_DRIFT.queueName(), "scheduled-scan");
    }

    @Scheduled(cron = "${argus.schedule.alert-noise:0 0 */4 * * *}")
    public void triggerAlertNoise() {
        log.info("Scheduled trigger: ALERT_NOISE");
        queuePort.publish(AgentType.ALERT_NOISE.queueName(), "scheduled-scan");
    }
}
