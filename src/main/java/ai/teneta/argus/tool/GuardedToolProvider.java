package ai.teneta.argus.tool;

import ai.teneta.argus.shared.AgentType;
import ai.teneta.argus.shared.RunContext;
import ai.teneta.argus.shared.RunContextHolder;
import ai.teneta.argus.audit.AuditEvent;
import ai.teneta.argus.audit.AuditService;
import ai.teneta.argus.hitl.HitlService;
import ai.teneta.argus.tool.sanitizer.DataSource;
import ai.teneta.argus.tool.sanitizer.PromptInjectionSanitizer;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class GuardedToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(GuardedToolProvider.class);

    private final McpToolProvider delegate;
    private final List<LocalTool> localTools;
    private final ToolAllowList allowList;
    private final HitlService hitlService;
    private final AuditService auditService;
    private final PromptInjectionSanitizer sanitizer;

    public GuardedToolProvider(
            McpToolProvider delegate,
            List<LocalTool> localTools,
            ToolAllowList allowList,
            HitlService hitlService,
            AuditService auditService,
            PromptInjectionSanitizer sanitizer) {
        this.delegate = delegate;
        this.localTools = localTools;
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

        // 1. Collect tools from MCP servers and from in-process LocalTool beans
        Map<ToolSpecification, ToolExecutor> available = new LinkedHashMap<>();
        available.putAll(delegate.provideTools(request).tools());
        for (LocalTool tool : localTools) {
            available.put(tool.specification(), tool.executor());
        }

        // 2. Filter to only allow-listed tools for this agent type
        Map<ToolSpecification, ToolExecutor> guarded = new LinkedHashMap<>();
        for (Map.Entry<ToolSpecification, ToolExecutor> entry : available.entrySet()) {
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

            // 5. Execute (MCP or local)
            String result;
            try {
                result = delegate.execute(executionRequest, memoryId);
            } catch (Exception e) {
                auditService.publish(AuditEvent.failed(agentRunId, toolName, e.getMessage()));
                throw e;
            }

            // 6. Sanitize tool response before returning to LLM
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
