package com.mohistmc.youer.ai.tool;

import com.mohistmc.youer.ai.model.AiMessage;
import com.mohistmc.youer.ai.provider.AiProvider;
import com.mohistmc.youer.api.ai.tool.AiToolContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AiAgentRequest(
        AiProvider provider,
        List<AiMessage> messages,
        AiToolRegistry.Snapshot tools,
        AiToolContext context,
        int maxSteps,
        int maxCallsPerTurn,
        AiToolExecutionLedger ledger,
        String correlationId) {
    public AiAgentRequest(AiProvider provider, List<AiMessage> messages,
            AiToolRegistry.Snapshot tools, AiToolContext context,
            int maxSteps, int maxCallsPerTurn) {
        this(provider, messages, tools, context, maxSteps, maxCallsPerTurn,
                new AiToolExecutionLedger(), UUID.randomUUID().toString());
    }
    public AiAgentRequest(AiProvider provider, List<AiMessage> messages,
            AiToolRegistry.Snapshot tools, AiToolContext context,
            int maxSteps, int maxCallsPerTurn, AiToolExecutionLedger ledger) {
        this(provider, messages, tools, context, maxSteps, maxCallsPerTurn,
                ledger, UUID.randomUUID().toString());
    }
    public AiAgentRequest {
        Objects.requireNonNull(provider, "provider");
        messages = List.copyOf(messages);
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(ledger, "ledger");
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("Agent correlation ID must not be blank");
        }
        if (maxSteps < 1 || maxCallsPerTurn < 1) throw new IllegalArgumentException("Agent limits must be positive");
    }
}
