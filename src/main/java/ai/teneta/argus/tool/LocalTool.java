package ai.teneta.argus.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;

public interface LocalTool {

    ToolSpecification specification();

    ToolExecutor executor();
}
