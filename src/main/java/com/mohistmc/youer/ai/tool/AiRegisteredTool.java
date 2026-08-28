package com.mohistmc.youer.ai.tool;

import com.mohistmc.youer.api.ai.tool.AiToolDefinition;
import com.mohistmc.youer.api.ai.tool.AiToolHandler;
import java.util.Objects;

public record AiRegisteredTool(
        AiToolOwner owner, AiToolDefinition definition, AiToolHandler handler,
        AiToolArgumentPreparer argumentPreparer) {

    public AiRegisteredTool(AiToolOwner owner, AiToolDefinition definition, AiToolHandler handler) {
        this(owner, definition, handler, AiToolArgumentPreparer.identity());
    }

    public AiRegisteredTool {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(argumentPreparer, "argumentPreparer");
    }
}
