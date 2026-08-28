package com.mohistmc.youer.ai.tool;

import com.mohistmc.mjson.Json;
import com.mohistmc.youer.ai.model.AiToolCallContent;
import com.mohistmc.youer.api.ai.tool.AiToolContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

public final class AiToolCallPreparer {

    private final AiToolSchemaValidator validator;

    public AiToolCallPreparer(AiToolSchemaValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public AiPreparedToolCall prepare(
            AiToolContext context,
            AiRegisteredTool tool,
            AiToolCallContent call,
            String correlationId,
            Instant now) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(now, "now");
        if (!tool.owner().isEnabled() || !tool.definition().name().equals(call.name())) {
            throw new IllegalArgumentException("Tool is unavailable");
        }
        Json copied = Json.read(call.arguments().toString());
        AiToolArgumentPreparer.PreparedArguments prepared =
                tool.argumentPreparer().prepare(context, tool.definition(), copied);
        Json normalized = Json.read(prepared.arguments().toString());
        List<String> errors = validator.validate(tool.definition().inputSchema(), normalized);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid Tool arguments: " + String.join("; ", errors));
        }
        String digest = digest(normalized);
        Component summary = prepared.displaySummary();
        if (tool.owner().source() == AiToolSource.CONFIGURED_HTTP) {
            summary = summary.append(Component.text(" digest=" + digest));
        }
        return new AiPreparedToolCall(
                correlationId, context, tool, call.id(), normalized, summary,
                digest, now, now.plus(tool.definition().timeout()));
    }

    private static String digest(Json arguments) {
        try {
            byte[] value = MessageDigest.getInstance("SHA-256")
                    .digest(arguments.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
