package ai.teneta.argus.tool.sanitizer;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "argus.sanitizer")
public record SanitizerProperties(Map<String, Integer> maxChars) {

    public int resolveMaxChars(DataSource source) {
        if (maxChars != null) {
            Integer configured = maxChars.get(source.label());
            if (configured != null) {
                return configured;
            }
        }
        return source.defaultMaxChars();
    }
}
