package com.mohistmc.youer.ai.model;

import java.util.Map;
import java.util.Objects;

public record AiToolResultContent(
        String callId, String name, String content, boolean error, Map<String, String> attributes)
        implements AiContentPart {

    public AiToolResultContent(String callId, String name, String content, boolean error) {
        this(callId, name, content, error, Map.of());
    }

    public AiToolResultContent {
        if (callId == null || callId.isBlank()) {
            throw new IllegalArgumentException("Tool result call ID must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool result name must not be blank");
        }
        Objects.requireNonNull(content, "content");
        attributes = Map.copyOf(attributes);
    }
}
