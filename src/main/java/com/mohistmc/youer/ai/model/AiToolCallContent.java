package com.mohistmc.youer.ai.model;

import com.mohistmc.mjson.Json;
import java.util.Map;
import java.util.Objects;

public record AiToolCallContent(
        String id, String name, Json arguments, Map<String, String> attributes)
        implements AiContentPart {

    public AiToolCallContent(String id, String name, Json arguments) {
        this(id, name, arguments, Map.of());
    }

    public AiToolCallContent {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Tool call ID must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool call name must not be blank");
        }
        Objects.requireNonNull(arguments, "arguments");
        if (!arguments.isObject()) {
            throw new IllegalArgumentException("Tool call arguments must be an object");
        }
        arguments = Json.read(arguments.toString());
        attributes = Map.copyOf(attributes);
    }
}
