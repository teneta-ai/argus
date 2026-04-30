# Agent Module

## Purpose

The agent module contains the AI agent definitions and the orchestrator that runs them.
Each agent is a LangChain4j `AiService` interface with a system prompt. The
`AgentOrchestrator` subscribes to per-agent queues via `QueuePort`
(`argus-version-drift-queue`, `argus-alert-noise-queue`), sets up the `AgentRunContext`
(ThreadLocal with agent run ID and type), dispatches to the appropriate agent, and
publishes completion events. All agents use `GuardedToolProvider` exclusively — no
`@Tool` annotations, no `.tools()` calls.

## Public API

- `AgentType` — enum defining agent types with descriptions, job descriptions, and queue names
- `AgentRunContext` — ThreadLocal context for current agent run (used by GuardedToolProvider)
- `AgentCompletedEvent` — Spring event published on agent completion
- `AgentOrchestrator` — consumes per-agent queues, runs agents

## Events Published

- `AgentCompletedEvent` — published when an agent run completes successfully

## Events Consumed

None (subscribes to queues via QueuePort, not Spring events).

## Queue Interactions

- **Reads from**: `argus-version-drift-queue`, `argus-alert-noise-queue` — receives raw payload strings
- **Writes to**: `argus-audit-queue` (indirectly via `AuditService`)

## MCP Interactions

None directly — agents interact with MCP servers through `GuardedToolProvider`.

## Configuration

```yaml
langchain4j:
  google-ai-gemini:
    chat-model:
      api-key: ${GEMINI_API_KEY}
      model-name: gemini-1.5-pro
```

## Do not depend on

- `trigger` — agent module consumes from queue, not from trigger directly
- `hitl` — HITL gating is handled by GuardedToolProvider, not agents
- `security` — LLM output validation is called by the orchestrator, not agents
