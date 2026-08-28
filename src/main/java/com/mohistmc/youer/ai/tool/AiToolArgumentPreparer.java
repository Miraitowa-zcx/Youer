package com.mohistmc.youer.ai.tool;

import com.mohistmc.mjson.Json;
import com.mohistmc.youer.api.ai.tool.AiToolContext;
import com.mohistmc.youer.api.ai.tool.AiToolDefinition;
import java.util.Objects;
import net.kyori.adventure.text.Component;

@FunctionalInterface
public interface AiToolArgumentPreparer {

    PreparedArguments prepare(AiToolContext context, AiToolDefinition definition, Json arguments);

    static AiToolArgumentPreparer identity() {
        return (context, definition, arguments) -> new PreparedArguments(
                Json.read(arguments.toString()), Component.text(definition.name()));
    }

    record PreparedArguments(Json arguments, Component displaySummary) {
        public PreparedArguments {
            Objects.requireNonNull(arguments, "arguments");
            if (!arguments.isObject()) {
                throw new IllegalArgumentException("Prepared Tool arguments must be an object");
            }
            arguments = Json.read(arguments.toString());
            displaySummary = Objects.requireNonNull(displaySummary, "displaySummary");
        }
    }
}
