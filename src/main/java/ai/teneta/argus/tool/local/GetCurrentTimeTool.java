package ai.teneta.argus.tool.local;

import ai.teneta.argus.tool.LocalTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class GetCurrentTimeTool implements LocalTool {

    public static final String NAME = "get_current_time";

    private final Clock clock;
    private final ObjectMapper objectMapper;

    public GetCurrentTimeTool(Clock clock, ObjectMapper objectMapper) {
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Returns the current wall-clock time as an ISO-8601 timestamp. " +
                        "Optionally accepts an IANA timezone (e.g., \"UTC\", \"America/New_York\"); " +
                        "defaults to UTC.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("timezone", "IANA timezone identifier; defaults to UTC")
                        .build())
                .build();
    }

    @Override
    public ToolExecutor executor() {
        return (request, memoryId) -> {
            ZoneId zone = parseZone(request.arguments());
            ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
            return now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        };
    }

    private ZoneId parseZone(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            JsonNode node = objectMapper.readTree(argumentsJson).get("timezone");
            if (node == null || !node.isTextual()) {
                return ZoneId.of("UTC");
            }
            return ZoneId.of(node.asText());
        } catch (DateTimeException | java.io.IOException e) {
            return ZoneId.of("UTC");
        }
    }
}
