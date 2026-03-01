package com.company.argus.tool.sanitizer;

public enum DataSource {

    JIRA("jira", 4000),
    GRAFANA("grafana", 1000),
    WEBHOOK_PAYLOAD("webhook-payload", 2000);

    private final String label;
    private final int maxChars;

    DataSource(String label, int maxChars) {
        this.label = label;
        this.maxChars = maxChars;
    }

    public String label() {
        return label;
    }

    public int maxChars() {
        return maxChars;
    }

    public static DataSource fromToolName(String toolName) {
        if (toolName.startsWith("jira_")) {
            return JIRA;
        }
        if (toolName.startsWith("grafana_")) {
            return GRAFANA;
        }
        return WEBHOOK_PAYLOAD;
    }
}
