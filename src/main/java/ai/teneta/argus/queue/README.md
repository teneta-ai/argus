# Queue Module

## Purpose

The queue module provides an abstraction layer over message queue infrastructure (currently
Redis Lists). It exposes a `QueuePort` interface that other modules use to publish and subscribe
to queues without coupling to the underlying Redis implementation. This ensures that all queue
interactions are centralized and that swapping queue providers requires changes only within this module.

## Public API

- `QueuePort` — interface for publishing messages and subscribing to queues
- `QueuePort.MessageHandler` — functional interface for message consumers
- `QueueNames` — constants for all queue names used in the system

## Events Published

None.

## Events Consumed

None.

## Queue Interactions

- **argus-version-drift-queue** — triggers and schedules publish payloads for Version Drift agent
- **argus-alert-noise-queue** — triggers and schedules publish payloads for Alert Noise agent
- **argus-hitl-request-queue** — used by HITL module for approval request processing
- **argus-audit-queue** — used by audit module for fire-and-forget audit event publishing

## MCP Interactions

None.

## Configuration

```yaml
spring:
  data:
    redis:
      url: ${SPRING_DATA_REDIS_URL:redis://localhost:6379}
```

Queues are Redis Lists keyed by queue name (see `QueueNames`). No pre-provisioning required.

## Do not depend on

- `agent` — queue is a low-level infrastructure module
- `tool` — no tool interactions
- `hitl` — no HITL awareness
- `trigger` — no trigger awareness
- `security` — no security concerns
- `audit` — no audit concerns
