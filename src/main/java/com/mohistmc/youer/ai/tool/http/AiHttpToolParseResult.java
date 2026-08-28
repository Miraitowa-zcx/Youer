package com.mohistmc.youer.ai.tool.http;

import java.util.List;

public record AiHttpToolParseResult(
        List<AiHttpToolDefinition> validDefinitions, List<Failure> failures) {

    public AiHttpToolParseResult {
        validDefinitions = List.copyOf(validDefinitions);
        failures = List.copyOf(failures);
    }

    public record Failure(int index, String name, String category, String message) {
    }
}
