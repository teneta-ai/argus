package ai.teneta.argus.tool.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class LocalToolConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
