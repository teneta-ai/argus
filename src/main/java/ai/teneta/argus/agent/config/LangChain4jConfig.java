package ai.teneta.argus.agent.config;

import ai.teneta.argus.agent.impl.AlertNoiseAgent;
import ai.teneta.argus.agent.impl.VersionDriftAgent;
import ai.teneta.argus.tool.GuardedToolProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChain4jConfig {

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
