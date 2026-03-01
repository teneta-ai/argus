# Security Module

## Purpose

The security module validates LLM outputs before the orchestrator acts on them.
`LlmOutputValidator` checks for: tool name references not in the allow-list,
identity denial (claims to be human), and system prompt injection attempts.
Rejected outputs are logged as audit events.

## Public API

- `LlmOutputValidator` — validates LLM output strings
- `LlmOutputValidator.ValidationResult` — accepted/rejected with reason

## Events Published

None (publishes audit events via `AuditService`).

## Events Consumed

None.

## Queue Interactions

- **Writes to**: `argus-audit-queue` (indirectly via `AuditService`)

## MCP Interactions

None.

## Configuration

No dedicated configuration — depends on `argus.tools.allow-list` from tool module.

## Do not depend on

- `agent` — security does not know about agent implementations
- `hitl` — no HITL awareness
- `trigger` — no trigger awareness
- `queue` — no direct queue interactions
