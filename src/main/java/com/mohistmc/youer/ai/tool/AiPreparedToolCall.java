package com.mohistmc.youer.ai.tool;

import com.mohistmc.mjson.Json;
import com.mohistmc.youer.api.ai.tool.AiToolContext;
import java.time.Instant;
import java.util.Objects;
import net.kyori.adventure.text.Component;

public record AiPreparedToolCall(
        String correlationId,
        AiToolContext context,
        AiRegisteredTool tool,
        String providerCallId,
        Json arguments,
        Component displaySummary,
        String argumentDigest,
        Instant preparedAt,
        Instant deadline) {

    public AiPreparedToolCall {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("Correlation ID must not be blank");
        }
        if (providerCallId == null || providerCallId.isBlank()) {
            throw new IllegalArgumentException("Provider call ID must not be blank");
        }
        context = Objects.requireNonNull(context, "context");
        tool = Objects.requireNonNull(tool, "tool");
        arguments = Json.read(Objects.requireNonNull(arguments, "arguments").toString());
        displaySummary = Objects.requireNonNull(displaySummary, "displaySummary");
        if (argumentDigest == null || argumentDigest.isBlank()) {
            throw new IllegalArgumentException("Argument digest must not be blank");
        }
        preparedAt = Objects.requireNonNull(preparedAt, "preparedAt");
        deadline = Objects.requireNonNull(deadline, "deadline");
        if (deadline.isBefore(preparedAt)) {
            throw new IllegalArgumentException("Prepared Tool deadline precedes preparation");
        }
    }
}
