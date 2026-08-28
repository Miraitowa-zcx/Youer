package com.mohistmc.youer.ai.tool;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public record AiToolExecutionOutcome<T>(
        AiToolExecutionState state, CompletionStage<T> completion) {
    public AiToolExecutionOutcome {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(completion, "completion");
    }
}
