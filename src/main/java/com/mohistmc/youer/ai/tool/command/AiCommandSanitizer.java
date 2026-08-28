package com.mohistmc.youer.ai.tool.command;

import com.mohistmc.mjson.Json;
import com.mohistmc.youer.ai.tool.AiToolArgumentPreparer;
import com.mohistmc.youer.api.ai.tool.AiToolContext;
import com.mohistmc.youer.api.ai.tool.AiToolDefinition;
import net.kyori.adventure.text.Component;

public final class AiCommandSanitizer implements AiToolArgumentPreparer {
    public String normalize(String command) {
        if (command == null) throw new IllegalArgumentException("Command is required");
        String value = command.trim();
        if (value.startsWith("/")) value = value.substring(1).trim();
        if (value.isBlank()) throw new IllegalArgumentException("Command must not be blank");
        if (value.indexOf(';') >= 0) throw new IllegalArgumentException("Command chaining is not allowed");
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException("Control characters are not allowed");
            }
        }
        if (value.length() > 2_048) {
            throw new IllegalArgumentException("Command exceeds 2048 characters");
        }
        return value;
    }

    @Override
    public PreparedArguments prepare(AiToolContext context, AiToolDefinition definition, Json arguments) {
        String command = normalize(arguments.at("command").asString());
        return new PreparedArguments(Json.object().set("command", command), Component.text(command));
    }
}
