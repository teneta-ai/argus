# Tool Module

## Purpose

The tool module is the security chokepoint for all LLM tool calls. It manages MCP client
connections (Atlassian Jira/Confluence and Grafana), enforces the YAML-based tool allow-list,
gates write operations through HITL approval, sanitizes MCP responses against prompt injection,
and publishes audit events for every tool invocation. The `GuardedToolProvider` is the single
entry point — all tool calls flow through it regardless of which MCP server backs them.

## Public API

- `GuardedToolProvider` — implements `ToolProvider`, used by all agent `AiServices`
- `ToolAllowList` — `@ConfigurationProperties` bean for the allow-list
- `ToolAllowListEntry` — individual allow-list entry record
- `McpClientRegistry` — registry of named MCP clients
- `PromptInjectionSanitizer` — sanitizes MCP responses before returning to LLM
- `DataSource` — enum mapping tool names to data sources with max char limits

## Events Published

None (publishes audit events via `AuditService`).

## Events Consumed

None.

## Queue Interactions

- **Writes to**: `argus-audit-queue` (indirectly via `AuditService`)

## MCP Interactions

- **Atlassian MCP Server** — `https://mcp.atlassian.com/v1/mcp` (Jira + Confluence + Compass)
- **Grafana MCP Server** — `http://mcp-grafana:8000/sse` (local Docker container)

## Configuration

```yaml
argus:
  mcp:
    atlassian:
      url: ${ARGUS_MCP_ATLASSIAN_URL}
      email: ${ATLASSIAN_EMAIL}
      api-token: ${ATLASSIAN_API_TOKEN}
    grafana:
      url: ${ARGUS_MCP_GRAFANA_URL}
  tools:
    allow-list:
      - agentType: VERSION_DRIFT
        toolName: jira_get_issue
        accessLevel: READ
        requiresHitl: false
      # ... (see application.yml for full list)
```

## Do not depend on

- `trigger` — tool module does not know about triggers
- `queue` — tool module does not interact with queues directly (uses AuditService)
