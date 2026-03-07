package ai.teneta.argus.security;

import ai.teneta.argus.audit.AuditEvent;
import ai.teneta.argus.audit.AuditService;
import ai.teneta.argus.tool.ToolAllowList;
import ai.teneta.argus.shared.RunContext;
import ai.teneta.argus.shared.RunContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class LlmOutputValidator {

    private static final Logger log = LoggerFactory.getLogger(LlmOutputValidator.class);

    private static final Pattern SYSTEM_PROMPT_PATTERN = Pattern.compile("^SYSTEM:", Pattern.MULTILINE);
    private static final Pattern IDENTITY_DENIAL_PATTERN = Pattern.compile(
            "I am (a |not an? )?(human|person|not an? AI|not artificial)",
            Pattern.CASE_INSENSITIVE);

    private final ToolAllowList allowList;
    private final AuditService auditService;

    public LlmOutputValidator(ToolAllowList allowList, AuditService auditService) {
        this.allowList = allowList;
        this.auditService = auditService;
    }

    public ValidationResult validate(String llmOutput, String referencedToolName) {
        RunContext ctx = RunContextHolder.current();
        UUID agentRunId = ctx.runId();

        // Tool name referenced is absent from allow-list
        if (referencedToolName != null
                && !allowList.isApproved(ctx.agentType(), referencedToolName)) {
            auditService.publish(AuditEvent.failed(agentRunId, referencedToolName,
                    "LLM referenced tool not in allow-list"));
            return ValidationResult.rejected("Tool " + referencedToolName + " not in allow-list");
        }

        // Claims to be human / denies being AI
        if (IDENTITY_DENIAL_PATTERN.matcher(llmOutput).find()) {
            auditService.publish(AuditEvent.failed(agentRunId, "LLM_OUTPUT",
                    "Identity denial detected"));
            return ValidationResult.rejected("Identity denial detected in LLM output");
        }

        // Attempts to set system prompt
        if (SYSTEM_PROMPT_PATTERN.matcher(llmOutput).find()) {
            auditService.publish(AuditEvent.failed(agentRunId, "LLM_OUTPUT",
                    "System prompt injection detected"));
            return ValidationResult.rejected("System prompt injection attempt detected");
        }

        return ValidationResult.ok();
    }

    public record ValidationResult(boolean accepted, String reason) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult rejected(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
