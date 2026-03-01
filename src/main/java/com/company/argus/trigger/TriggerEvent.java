package com.company.argus.trigger;

import com.company.argus.shared.AgentType;

public record TriggerEvent(AgentType agentType, String payload) {
}
