package ai.teneta.argus.tool.config;

import ai.teneta.argus.tool.McpClientRegistry;
import ai.teneta.argus.tool.ToolAllowList;
import ai.teneta.argus.tool.sanitizer.SanitizerProperties;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.McpTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Configuration
@EnableConfigurationProperties({ ToolAllowList.class, SanitizerProperties.class })
public class McpConfig {

    @Bean
    public McpClient atlassianMcpClient(
            @Value("${argus.mcp.atlassian.url}") String url,
            @Value("${argus.mcp.atlassian.email}") String email,
            @Value("${argus.mcp.atlassian.api-token}") String apiToken) {

        String credentials = Base64.getEncoder().encodeToString(
                (email + ":" + apiToken).getBytes(StandardCharsets.UTF_8));

        McpTransport transport = new HttpMcpTransport.Builder()
                .sseUrl(url)
                .customHeaders(Map.of("Authorization", "Basic " + credentials))
                .build();

        return new DefaultMcpClient.Builder()
                .transport(transport)
                .build();
    }

    @Bean
    public McpClient grafanaMcpClient(@Value("${argus.mcp.grafana.url}") String url) {
        McpTransport transport = new HttpMcpTransport.Builder()
                .sseUrl(url + "/sse")
                .build();

        return new DefaultMcpClient.Builder()
                .transport(transport)
                .build();
    }

    @Bean
    public McpClientRegistry mcpClientRegistry(
            McpClient atlassianMcpClient,
            McpClient grafanaMcpClient) {
        McpClientRegistry registry = new McpClientRegistry();
        registry.register("atlassian", atlassianMcpClient);
        registry.register("grafana", grafanaMcpClient);
        return registry;
    }

    @Bean
    public dev.langchain4j.mcp.McpToolProvider mcpToolProvider(
            McpClient atlassianMcpClient,
            McpClient grafanaMcpClient) {
        return dev.langchain4j.mcp.McpToolProvider.builder()
                .mcpClients(atlassianMcpClient, grafanaMcpClient)
                .failIfOneServerFails(false)
                .build();
    }
}
