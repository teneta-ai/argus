package com.company.argus.agent.config;

import com.company.argus.AgentType;
import com.company.argus.agent.impl.AlertNoiseAgent;
import com.company.argus.agent.impl.CsTriageAgent;
import com.company.argus.agent.impl.VersionDriftAgent;
import com.company.argus.tool.GuardedToolProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChain4jConfig {

    @Bean
    public CsTriageAgent csTriageAgent(
            ChatModel model,
            GuardedToolProvider guardedToolProvider) {
        return AiServices.builder(CsTriageAgent.class)
                .chatModel(model)
                .toolProvider(guardedToolProvider)
                .build();
    }

    @Bean
    public VersionDriftAgent versionDriftAgent(
            ChatModel model,
            GuardedToolProvider guardedToolProvider) {
        return AiServices.builder(VersionDriftAgent.class)
                .chatModel(model)
                .toolProvider(guardedToolProvider)
                .build();
    }

    @Bean
    public AlertNoiseAgent alertNoiseAgent(
            ChatModel model,
            GuardedToolProvider guardedToolProvider) {
        return AiServices.builder(AlertNoiseAgent.class)
                .chatModel(model)
                .toolProvider(guardedToolProvider)
                .build();
    }
}
