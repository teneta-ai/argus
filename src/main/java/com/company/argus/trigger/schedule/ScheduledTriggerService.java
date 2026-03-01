package com.company.argus.trigger.schedule;

import com.company.argus.shared.AgentType;
import com.company.argus.queue.QueueNames;
import com.company.argus.queue.QueuePort;
import com.company.argus.trigger.TriggerEvent;
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
        queuePort.publish(QueueNames.TRIGGER, new TriggerEvent(AgentType.VERSION_DRIFT, "scheduled-scan"));
    }

    @Scheduled(cron = "${argus.schedule.alert-noise:0 0 */4 * * *}")
    public void triggerAlertNoise() {
        log.info("Scheduled trigger: ALERT_NOISE");
        queuePort.publish(QueueNames.TRIGGER, new TriggerEvent(AgentType.ALERT_NOISE, "scheduled-scan"));
    }
}
