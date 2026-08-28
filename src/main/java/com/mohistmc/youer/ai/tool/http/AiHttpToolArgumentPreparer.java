package com.mohistmc.youer.ai.tool.http;

import com.mohistmc.mjson.Json;
import com.mohistmc.youer.ai.tool.AiToolArgumentPreparer;
import com.mohistmc.youer.api.ai.tool.AiToolContext;
import com.mohistmc.youer.api.ai.tool.AiToolDefinition;
import java.util.Objects;
import net.kyori.adventure.text.Component;

public final class AiHttpToolArgumentPreparer implements AiToolArgumentPreparer {

    private final AiHttpToolDefinition http;

    public AiHttpToolArgumentPreparer(AiHttpToolDefinition http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override
    public PreparedArguments prepare(
            AiToolContext context, AiToolDefinition definition, Json arguments) {
        String destination = http.uri().toString();
        for (String token : http.path().keySet()) {
            destination = destination
                    .replace("%7B" + token + "%7D", "<redacted>")
                    .replace("%7b" + token + "%7d", "<redacted>");
        }
        String separator = destination.contains("?") ? "&" : "?";
        StringBuilder summary = new StringBuilder(http.method()).append(' ').append(destination);
        for (String queryName : http.query().keySet()) {
            summary.append(separator).append(queryName).append("=<redacted>");
            separator = "&";
        }
        return new PreparedArguments(
                Json.read(arguments.toString()), Component.text(summary.toString()));
    }
}
