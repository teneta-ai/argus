package com.company.argus.tool.sanitizer;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PromptInjectionSanitizer {

    private static final Pattern IGNORE_PATTERN = Pattern.compile(
            "ignore.{0,20}(instruction|prompt|system)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISREGARD_PATTERN = Pattern.compile(
            "disregard.{0,20}(instruction|prompt)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PERSONA_PATTERN = Pattern.compile(
            "you are now|act as|new persona|jailbreak", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    private static final String FILTERED = "[FILTERED]";

    public String sanitize(String input, DataSource source) {
        if (input == null || input.isEmpty()) {
            return wrapXml("", source);
        }

        // 1. Hard truncate to source.maxChars()
        String result = input.length() > source.maxChars()
                ? input.substring(0, source.maxChars())
                : input;

        // 2. Strip control chars except \n \t
        result = CONTROL_CHARS.matcher(result).replaceAll("");

        // 3. Regex filter for prompt injection attempts
        result = IGNORE_PATTERN.matcher(result).replaceAll(FILTERED);
        result = DISREGARD_PATTERN.matcher(result).replaceAll(FILTERED);
        result = PERSONA_PATTERN.matcher(result).replaceAll(FILTERED);

        // 4. Wrap in XML isolation
        return wrapXml(result, source);
    }

    private String wrapXml(String content, DataSource source) {
        return "<external_data source=\"" + source.label() + "\">\n" + content + "\n</external_data>";
    }
}
