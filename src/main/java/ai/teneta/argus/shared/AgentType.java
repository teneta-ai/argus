package ai.teneta.argus.shared;

import ai.teneta.argus.queue.QueueNames;

public enum AgentType {

    VERSION_DRIFT(
            "Version Drift Detection Agent",
            """
            Detect version drift across environments. For the given service:
            1. Search Jira for existing drift issues.
            2. Query Grafana dashboards for environment health signals.
            3. If drift is detected, create a Jira issue (jira_create_issue) with: \
            drifted environments, Grafana dashboard links, and remediation steps.
            If no drift is detected, take no Jira action (audit-only)."""
    ),

    ALERT_NOISE(
            "Alert Noise Reduction Agent",
            """
            Evaluate Grafana alerts for noise. For the given alert rule or firing group:
            1. Query grafana_list_alert_rules and grafana_search_dashboards for context.
            2. Make a binary decision: actionable noise or expected/already-tracked.
            3. If actionable noise: create a Jira issue (jira_create_issue) to track it.
            4. If expected or already tracked: take no tool action (audit-only).
            State your reasoning clearly. Do not hedge."""
    );

    private final String description;
    private final String jobDescription;

    AgentType(String description, String jobDescription) {
        this.description = description;
        this.jobDescription = jobDescription;
    }

    public String description() {
        return description;
    }

    public String jobDescription() {
        return jobDescription;
    }

    public String queueName() {
        return switch (this) {
            case VERSION_DRIFT -> QueueNames.VERSION_DRIFT;
            case ALERT_NOISE -> QueueNames.ALERT_NOISE;
        };
    }
}
