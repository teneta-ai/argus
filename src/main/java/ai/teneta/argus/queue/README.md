# Queue Module

## Purpose

The queue module provides an abstraction layer over message queue infrastructure (currently
AWS SQS). It exposes a `QueuePort` interface that other modules use to publish and subscribe
to queues without coupling to the underlying SQS SDK. This ensures that all queue interactions
are centralized and that swapping queue providers requires changes only within this module.

## Public API

- `QueuePort` — interface for publishing messages and subscribing to queues
- `QueuePort.MessageHandler` — functional interface for message consumers
- `QueueNames` — constants for all queue names used in the system
- `QueueMessage` — record representing a received message with receipt handle

## Events Published

None.

## Events Consumed

None.

## Queue Interactions

- **argus-trigger-queue** — used by trigger module to enqueue agent triggers
- **argus-hitl-request-queue** — used by HITL module for approval request processing
- **argus-audit-queue** — used by audit module for fire-and-forget audit event publishing

## MCP Interactions

None.

## Configuration

```yaml
cloud:
  aws:
    sqs:
      endpoint: ${SQS_ENDPOINT}
      region: ${AWS_REGION}
    queues:
      trigger: ${ARGUS_TRIGGER_QUEUE_URL}
      hitl-request: ${ARGUS_HITL_REQUEST_QUEUE_URL}
      audit: ${ARGUS_AUDIT_QUEUE_URL}
```

## Do not depend on

- `agent` — queue is a low-level infrastructure module
- `tool` — no tool interactions
- `hitl` — no HITL awareness
- `trigger` — no trigger awareness
- `security` — no security concerns
- `audit` — no audit concerns
