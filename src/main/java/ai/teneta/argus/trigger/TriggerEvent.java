package ai.teneta.argus.trigger;

import ai.teneta.argus.shared.AgentType;

public record TriggerEvent(AgentType agentType, String payload) {
}
