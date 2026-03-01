# Trigger Module

## Purpose

The trigger module is the ingress point for all agent executions. It handles incoming
webhooks (both generic HMAC-signed and Jira Cloud webhooks) and scheduled cron triggers.
All triggers sanitize their payloads and publish `TriggerEvent` messages to the trigger
queue for async processing by the agent orchestrator.

## Public API

- `TriggerEvent` — record published to the trigger queue (agentType + payload)

## Events Published

None (publishes to SQS queue, not Spring events).

## Events Consumed

None.

## Queue Interactions

- **Writes to**: `argus-trigger-queue` — every trigger publishes a `TriggerEvent` message

## MCP Interactions

None.

## Configuration

```yaml
argus:
  webhook:
    hmac-secret: ${ARGUS_WEBHOOK_SECRET}
    timestamp-tolerance-seconds: 300
    deduplication-ttl-seconds: 3600
    jira:
      secret: ${ARGUS_JIRA_WEBHOOK_SECRET}
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
