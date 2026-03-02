# HITL Module

## Purpose

The Human-In-The-Loop (HITL) module manages approval workflows for high-risk tool
calls. When `GuardedToolProvider` encounters a tool marked `requiresHitl: true`,
it calls `HitlService.requestApproval()` which blocks the virtual thread until a
human approves, rejects, or the request times out. Notifications are sent via a
pluggable `HitlNotificationChannel` interface (currently Slack). Correlation state
is in-memory (`ConcurrentHashMap`) — no database required.

## Public API

- `HitlService` — core approval/resolution logic
- `HitlNotificationChannel` — interface for notification channel implementations
- `ApprovalRequest` — record representing a pending approval
- `ApprovalStatus` — enum: APPROVED, REJECTED, TIMED_OUT
- `ApprovalDeniedException` — thrown when approval is denied or times out
- `HitlCallbackController` — REST endpoint for Slack interaction callbacks

## Events Published

None.

## Events Consumed

None.

## Queue Interactions

None (in-memory ConcurrentHashMap for correlation).

## MCP Interactions

None.

## Configuration

```yaml
argus:
  hitl:
    slack:
      approval-timeout-minutes: 15
      channel-id: ${ARGUS_HITL_CHANNEL_ID}
      bot-token: ${ARGUS_SLACK_BOT_TOKEN}
      signing-secret: ${ARGUS_SLACK_SIGNING_SECRET}
```

## Do not depend on

- `agent` — HITL does not know about agent types or implementations
- `tool` — HITL is called by GuardedToolProvider, not the other way around
- `trigger` — no trigger awareness
- `security` — no security concerns
