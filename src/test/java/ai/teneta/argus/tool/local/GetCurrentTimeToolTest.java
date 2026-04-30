package ai.teneta.argus.tool.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class GetCurrentTimeToolTest {

    private static final Instant FIXED = Instant.parse("2026-04-30T12:34:56Z");

    private GetCurrentTimeTool toolWithFixedClock(ZoneId zone) {
        return new GetCurrentTimeTool(Clock.fixed(FIXED, zone), new ObjectMapper());
    }

    @Test
    void specificationDeclaresTimezoneParameter() {
        var spec = toolWithFixedClock(ZoneId.of("UTC")).specification();
        assertEquals(GetCurrentTimeTool.NAME, spec.name());
        assertNotNull(spec.parameters());
        assertTrue(spec.parameters().properties().containsKey("timezone"));
    }

    @Test
    void emptyArgumentsReturnsUtcTimestamp() {
        var tool = toolWithFixedClock(ZoneId.of("UTC"));
        ToolExecutionRequest req = ToolExecutionRequest.builder()
                .name(GetCurrentTimeTool.NAME).arguments("{}").build();

        String out = tool.executor().execute(req, "memory");

        assertEquals("2026-04-30T12:34:56Z", out);
    }

    @Test
    void honoursTimezoneArgument() {
        var tool = toolWithFixedClock(ZoneId.of("UTC"));
        ToolExecutionRequest req = ToolExecutionRequest.builder()
                .name(GetCurrentTimeTool.NAME)
                .arguments("{\"timezone\":\"America/New_York\"}")
                .build();

        String out = tool.executor().execute(req, "memory");

        assertTrue(out.endsWith("-04:00"), "Expected EDT offset, got: " + out);
        assertTrue(out.startsWith("2026-04-30T08:34:56"), "Wrong wall-clock time: " + out);
    }

    @Test
    void invalidTimezoneFallsBackToUtc() {
        var tool = toolWithFixedClock(ZoneId.of("UTC"));
        ToolExecutionRequest req = ToolExecutionRequest.builder()
                .name(GetCurrentTimeTool.NAME)
                .arguments("{\"timezone\":\"Not/A_Zone\"}")
                .build();

        String out = tool.executor().execute(req, "memory");

        assertEquals("2026-04-30T12:34:56Z", out);
    }

    @Test
    void nullArgumentsReturnsUtcTimestamp() {
        var tool = toolWithFixedClock(ZoneId.of("UTC"));
        ToolExecutionRequest req = ToolExecutionRequest.builder()
                .name(GetCurrentTimeTool.NAME).arguments(null).build();

        String out = tool.executor().execute(req, "memory");

        assertEquals("2026-04-30T12:34:56Z", out);
    }
}
