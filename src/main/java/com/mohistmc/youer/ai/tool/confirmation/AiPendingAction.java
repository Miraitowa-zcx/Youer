package com.mohistmc.youer.ai.tool.confirmation;

import com.mohistmc.youer.ai.tool.AiPreparedToolCall;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;

public record AiPendingAction(
        String id,
        UUID playerId,
        AiPreparedToolCall preparedCall,
        Component summary,
        Instant createdAt,
        Instant expiresAt,
        CompletableFuture<AiConfirmationDecision> future) {

    public CompletionStage<AiConfirmationDecision> decision() {
        return future;
    }
}
