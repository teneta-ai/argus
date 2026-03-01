package ai.teneta.argus.tool;

import dev.langchain4j.mcp.client.McpClient;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class McpClientRegistry {

    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();

    public void register(String key, McpClient client) {
        clients.put(key, client);
    }

    public McpClient get(String key) {
        McpClient client = clients.get(key);
        if (client == null) {
            throw new IllegalArgumentException("No MCP client registered with key: " + key);
        }
        return client;
    }

    public List<McpClient> all() {
        return List.copyOf(clients.values());
    }

    public Map<String, McpClient> allAsMap() {
        return Collections.unmodifiableMap(clients);
    }
}
