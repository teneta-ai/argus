package com.company.argus.tool;

import com.company.argus.shared.AgentType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ConfigurationProperties(prefix = "argus.tools")
public class ToolAllowList {

    private List<ToolAllowListEntry> allowList = List.of();
    private final Map<String, ToolAllowListEntry> index = new ConcurrentHashMap<>();

    public List<ToolAllowListEntry> getAllowList() {
        return allowList;
    }

    public void setAllowList(List<ToolAllowListEntry> allowList) {
        this.allowList = allowList;
        index.clear();
        for (ToolAllowListEntry entry : allowList) {
            index.put(key(entry.agentType(), entry.toolName()), entry);
        }
    }

    public boolean isApproved(AgentType agentType, String toolName) {
        return index.containsKey(key(agentType.name(), toolName));
    }

    public ToolAllowListEntry get(AgentType agentType, String toolName) {
        return Optional.ofNullable(index.get(key(agentType.name(), toolName)))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tool " + toolName + " not in allow-list for agent " + agentType));
    }

    private static String key(String agentType, String toolName) {
        return agentType + ":" + toolName;
    }
}
