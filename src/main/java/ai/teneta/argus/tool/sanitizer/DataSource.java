package ai.teneta.argus.tool.sanitizer;

public enum DataSource {

    JIRA("jira", 4000),
    GRAFANA("grafana", 1000),
    LOCAL("local", 1000),
    WEBHOOK_PAYLOAD("webhook-payload", 2000);

    private final String label;
    private final int defaultMaxChars;

    DataSource(String label, int defaultMaxChars) {
        this.label = label;
        this.defaultMaxChars = defaultMaxChars;
    }

    public String label() {
        return label;
    }

    public int defaultMaxChars() {
        return defaultMaxChars;
    }

    public static DataSource fromToolName(String toolName) {
        if (toolName.startsWith("jira_")) {
            return JIRA;
        }
        if (toolName.startsWith("grafana_")) {
            return GRAFANA;
        }
        if (toolName.startsWith("get_") || toolName.startsWith("local_")) {
            return LOCAL;
        }
        return WEBHOOK_PAYLOAD;
    }
}
