# Trigger Module

## Purpose

The trigger module is the ingress point for all agent executions. Each integrated
system has its own dedicated webhook controller (e.g., Jira Cloud). Scheduled cron
triggers handle periodic scans. All triggers sanitize their payloads and publish
directly to per-agent SQS queues for async processing by the agent orchestrator.

## Public API

None — triggers are entry points only; no types are exported.

## Events Published

None (publishes to SQS queues, not Spring events).

## Events Consumed

None.

## Queue Interactions

- **Writes to**: `argus-cs-triage-queue`, `argus-version-drift-queue`, `argus-alert-noise-queue` — each trigger publishes a sanitized payload to the target agent's queue

## MCP Interactions

None.

## Configuration

```yaml
argus:
  webhook:
    jira:
      secret: ${ARGUS_JIRA_WEBHOOK_SECRET}
      deduplication-ttl-seconds: 3600
  schedule:
    version-drift: "0 0 8 * * MON-FRI"
    alert-noise: "0 0 */4 * * *"
spring:
  data:
    redis:
      url: ${SPRING_DATA_REDIS_URL}
```

## Do not depend on

- `agent` — triggers don't run agents directly, they enqueue messages
- `hitl` — no HITL awareness at the trigger layer
- `audit` — no direct audit publishing
- `security` — no LLM output validation at trigger layer
