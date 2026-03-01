# Audit Module

## Purpose

The audit module provides fire-and-forget audit event publishing to SQS. Every tool
call attempt, success, failure, and block is recorded as an `AuditEvent`. The service
is intentionally resilient — audit failures never break the agent pipeline. Events
are published to the audit queue for downstream consumption by analytics or compliance
systems.

## Public API

- `AuditService` — publishes audit events to the audit queue
- `AuditEvent` — record with agent run ID, tool name, status, detail, and timestamp

## Events Published

None (publishes to SQS queue).

## Events Consumed

None.

## Queue Interactions

- **Writes to**: `argus-audit-queue` — all audit events published here

## MCP Interactions

None.

## Configuration

```yaml
cloud:
  aws:
    queues:
      audit: ${ARGUS_AUDIT_QUEUE_URL}
```

## Do not depend on

- `agent` — audit doesn't know about agent types
- `tool` — audit is called by tool module, not the other way around
- `hitl` — no HITL awareness
- `trigger` — no trigger awareness
- `security` — no security concerns
