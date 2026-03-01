package com.company.argus.tool;

import com.company.argus.shared.AgentType;
import com.company.argus.shared.RunContext;
import com.company.argus.shared.RunContextHolder;
import com.company.argus.audit.AuditEvent;
import com.company.argus.audit.AuditService;
import com.company.argus.hitl.HitlService;
import com.company.argus.tool.sanitizer.DataSource;
import com.company.argus.tool.sanitizer.PromptInjectionSanitizer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class GuardedToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(GuardedToolProvider.class);

    private final McpToolProvider delegate;
    private final ToolAllowList allowList;
    private final HitlService hitlService;
    private final AuditService auditService;
    private final PromptInjectionSanitizer sanitizer;

    public GuardedToolProvider(
            McpToolProvider delegate,
            ToolAllowList allowList,
            HitlService hitlService,
            AuditService auditService,
            PromptInjectionSanitizer sanitizer) {
        this.delegate = delegate;
        this.allowList = allowList;
        this.hitlService = hitlService;
        this.auditService = auditService;
        this.sanitizer = sanitizer;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        RunContext ctx = RunContextHolder.current();
        AgentType agentType = ctx.agentType();
        UUID agentRunId = ctx.runId();

        // 1. Get all tools from all MCP servers
        ToolProviderResult mcpResult = delegate.provideTools(request);

        // 2. Filter to only allow-listed tools for this agent type
        Map<ToolSpecification, ToolExecutor> guarded = new LinkedHashMap<>();
        for (Map.Entry<ToolSpecification, ToolExecutor> entry : mcpResult.tools().entrySet()) {
            String toolName = entry.getKey().name();
            if (allowList.isApproved(agentType, toolName)) {
                guarded.put(entry.getKey(),
                        wrapWithGuard(toolName, entry.getValue(), agentRunId, agentType));
            } else {
                log.debug("Tool {} not in allow-list for agent {}, skipping", toolName, agentType);
                auditService.publish(AuditEvent.blocked(agentRunId, toolName));
            }
        }

        return ToolProviderResult.builder().addAll(guarded).build();
    }

    private ToolExecutor wrapWithGuard(
            String toolName, ToolExecutor delegate,
            UUID agentRunId, AgentType agentType) {

        return (executionRequest, memoryId) -> {
            // 3. Audit: log ATTEMPTED before execution
            auditService.publish(AuditEvent.attempted(agentRunId, toolName, executionRequest));

            // 4. HITL gate if required
            ToolAllowListEntry entry = allowList.get(agentType, toolName);
            if (entry.requiresHitl()) {
                hitlService.requestApproval(agentRunId, toolName, executionRequest);
                // blocks virtual thread until approved / rejected / timed out
            }

            // 5. Execute via MCP
            String result;
            try {
                result = delegate.execute(executionRequest, memoryId);
            } catch (Exception e) {
                auditService.publish(AuditEvent.failed(agentRunId, toolName, e.getMessage()));
                throw e;
            }

            // 6. Sanitize MCP response before returning to LLM
            DataSource source = DataSource.fromToolName(toolName);
            String sanitized = sanitizer.sanitize(result, source);

            // 7. Audit: log SUCCESS
            auditService.publish(AuditEvent.success(agentRunId, toolName, sanitized));

            // 8. AI disclaimer for write tools
            if (entry.accessLevel() == ToolAllowListEntry.AccessLevel.READ_WRITE) {
                log.debug("READ_WRITE tool executed: {} — disclaimer expected from MCP server", toolName);
            }

            return sanitized;
        };
    }
}
