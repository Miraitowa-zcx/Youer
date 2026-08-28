package com.mohistmc.youer.ai.model;

import java.util.List;
import com.mohistmc.youer.api.ai.tool.AiToolDefinition;

public record AiChatRequest(
        List<AiMessage> messages, List<AiToolDefinition> tools, String correlationId) {

    public AiChatRequest {
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
        correlationId = correlationId == null ? "" : correlationId;
    }

    public AiChatRequest(List<AiMessage> messages, List<AiToolDefinition> tools) {
        this(messages, tools, "");
    }

    public AiChatRequest(List<AiMessage> messages) {
        this(messages, List.of(), "");
    }

    public AiChatRequest withCorrelationId(String value) {
        return new AiChatRequest(messages, tools, value);
    }
}
