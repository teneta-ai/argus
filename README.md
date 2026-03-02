# Project Argus

Agentic Infrastructure Service — AI agents triggered by webhooks and schedules that automate
internal ops tasks using LLM reasoning over MCP-backed tools.

## What It Does

Argus runs AI agents that automate internal operations:

- **CS Triage** — Triages customer support tickets by correlating Jira issues with Grafana metrics
- **Version Drift Detection** — Detects version drift across environments and creates tracking issues
- **Alert Noise Reduction** — Evaluates Grafana alerts and filters actionable noise from expected patterns

## Module Map

```
argus/
├── trigger/    ← Webhooks + scheduled cron triggers → publish to SQS
├── queue/      ← QueuePort abstraction over AWS SQS
├── agent/      ← AI agent definitions + AgentOrchestrator
├── tool/       ← GuardedToolProvider + MCP client config + sanitizer
├── hitl/       ← Human-In-The-Loop approval workflows (Slack)
├── audit/      ← Fire-and-forget audit event publishing
├── security/   ← LLM output validation
└── shared/     ← AgentType enum, RunContext, RunContextHolder
```

## Queue Topology

Each agent has its own dedicated SQS queue. Webhooks and schedules publish directly
to the target agent's queue. The orchestrator subscribes to all agent queues.

```
[Webhook / Schedule]
        │
        ├──► argus-cs-triage-queue ──────► AgentOrchestrator → CsTriageAgent
        ├──► argus-version-drift-queue ──► AgentOrchestrator → VersionDriftAgent
        └──► argus-alert-noise-queue ────► AgentOrchestrator → AlertNoiseAgent

[AgentOrchestrator]
        │
        ├──(requiresHitl=true)──► HitlService (in-memory ConcurrentHashMap)
        │                                │
        │                      [Slack interactive message]
        │                                │
        │                      [User clicks Approve / Reject]
        │                                │
        │                      POST /hitl/slack/interaction
        │
        ├──(tool call)──► MCP server (Jira / Grafana)
        │
        └──(every tool call)──► argus-audit-queue (fire-and-forget)
```

## MCP Server Inventory

| Integration | Type | Endpoint | Auth |
|---|---|---|---|
| Atlassian (Jira + Confluence) | Remote — Atlassian-hosted | `https://mcp.atlassian.com/v1/mcp` | API token (Basic) |
| Grafana Cloud | Local Docker container | `http://mcp-grafana:8000/sse` | Service account token |

## Local Dev Quickstart

### Prerequisites

- Java 21
- Maven 3.9+
- Docker + Docker Compose

### 1. Set environment variables

```bash
cp .env.example .env
# Fill in your actual values in .env
```

Or export them directly:

```bash
export ATLASSIAN_EMAIL=your@email.com
export ATLASSIAN_API_TOKEN=your-atlassian-api-token
export GRAFANA_URL=https://your-org.grafana.net
export GRAFANA_SERVICE_ACCOUNT_TOKEN=your-grafana-token
export GEMINI_API_KEY=your-gemini-api-key
```

### 2. Start infrastructure

```bash
docker compose up -d redis localstack localstack-init mcp-grafana
```

### 3. Run the service

```bash
export SQS_ENDPOINT=http://localhost:4566
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export ARGUS_CS_TRIAGE_QUEUE_URL=http://localhost:4566/000000000000/argus-cs-triage-queue
export ARGUS_VERSION_DRIFT_QUEUE_URL=http://localhost:4566/000000000000/argus-version-drift-queue
export ARGUS_ALERT_NOISE_QUEUE_URL=http://localhost:4566/000000000000/argus-alert-noise-queue
export ARGUS_HITL_REQUEST_QUEUE_URL=http://localhost:4566/000000000000/argus-hitl-request-queue
export ARGUS_AUDIT_QUEUE_URL=http://localhost:4566/000000000000/argus-audit-queue
export ARGUS_JIRA_WEBHOOK_SECRET=local-jira-secret
export ARGUS_HITL_CHANNEL_ID=C0000000000
export ARGUS_SLACK_BOT_TOKEN=xoxb-local
export ARGUS_SLACK_SIGNING_SECRET=local-signing-secret

mvn spring-boot:run
```

### 4. Send a test Jira webhook

```bash
BODY='{"webhookEvent":"jira:issue_updated","issue":{"key":"CS-1234"}}'
SIG="sha256=$(echo -n "${BODY}" | openssl dgst -sha256 -hmac "local-jira-secret" | awk '{print $2}')"

curl -X POST http://localhost:8080/webhook/jira/CS_TRIAGE \
  -H "Content-Type: application/json" \
  -H "X-Hub-Signature: ${SIG}" \
  -H "X-Atlassian-Webhook-Identifier: test-$(date +%s)" \
  -d "${BODY}"
```

## Running Tests

```bash
docker compose run tests
```

This runs `mvn test` in a disposable Maven container with a shared cache volume — no local Java install required.

## How to Add a New Agent

1. Add a new value to `AgentType` enum with `description()`, `jobDescription()`, and `queueName()`
2. Add a new constant to `QueueNames`
3. Add the queue URL to `SqsQueueProperties` and its `resolveUrl()` switch
4. Create a new agent interface in `agent/impl/` with `@SystemMessage`
5. Add a bean in `LangChain4jConfig` using `AiServices.builder()` with `.toolProvider(guardedToolProvider)`
6. Subscribe to the new queue in `AgentOrchestrator.startListening()` and add a case to `runAgent()`
7. Add the queue to `docker-compose.yml` (localstack-init + argus env vars) and `application.yml`
8. Add the agent's required tools to the allow-list in `application.yml`

## How to Add a New Webhook Integration

1. Create a dedicated controller (e.g., `GrafanaWebhookController`) under `trigger/webhook/`
2. Create a corresponding auth filter (e.g., `GrafanaWebhookAuthFilter`) with the integration's auth scheme
3. Add the integration's config under `argus.webhook.<name>` in `application.yml`
4. The controller sanitizes the payload and publishes to `agentType.queueName()`

Each integration gets its own endpoint, auth filter, and secret — no shared generic webhook.

## How to Add a New Tool (MCP Server)

1. Add a new MCP server container to `docker-compose.yml` (or use a remote endpoint)
2. Add a new `McpClient` bean in `McpConfig`
3. Add the client to the `McpToolProvider` bean
4. Register it in `McpClientRegistry`
5. Add tool entries to the allow-list in `application.yml` for each agent that needs access
6. Add a mapping in `DataSource.fromToolName()` for sanitization

No code changes are required in agents or `GuardedToolProvider`.

## How to Add a New HITL Notification Channel

1. Create a new class implementing `HitlNotificationChannel`
2. Annotate with `@Component` and `@ConditionalOnProperty` gated on a required config key (e.g., `argus.hitl.teams.bot-token`)
3. Implement `sendApprovalRequest()`, `updateWithDecision()`, and `channelName()`
4. Add a callback controller if the channel supports interactive responses
5. Add the channel's config block under `argus.hitl.<channel-name>` in `application.yml`

`HitlService` depends only on the `HitlNotificationChannel` interface — no Slack-specific types leak.
