package ai.teneta.argus.tool;

import dev.langchain4j.mcp.client.McpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpClientRegistryTest {

    private McpClientRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new McpClientRegistry();
    }

    @Test
    void getThrowsWhenMcpServerNotRegistered() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.get("nonexistent-server"));

        assertTrue(ex.getMessage().contains("nonexistent-server"));
    }

    @Test
    void registerAndRetrieveClient() {
        McpClient client = mock(McpClient.class);
        registry.register("jira-server", client);

        assertSame(client, registry.get("jira-server"));
    }

    @Test
    void allReturnsAllRegisteredClients() {
        McpClient jira = mock(McpClient.class);
        McpClient grafana = mock(McpClient.class);
        registry.register("jira", jira);
        registry.register("grafana", grafana);

        assertEquals(2, registry.all().size());
        assertTrue(registry.all().contains(jira));
        assertTrue(registry.all().contains(grafana));
    }

    @Test
    void allAsMapReturnsUnmodifiableView() {
        McpClient client = mock(McpClient.class);
        registry.register("jira", client);

        assertThrows(UnsupportedOperationException.class,
                () -> registry.allAsMap().put("hack", mock(McpClient.class)));
    }
}
