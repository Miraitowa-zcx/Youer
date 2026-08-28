package com.mohistmc.youer.ai.tool;

import java.util.logging.Logger;
import java.util.function.Consumer;

public final class AiToolAudit {
    private final Logger logger;
    private final Consumer<AiToolExecutionState> outcomeListener;

    public AiToolAudit(Logger logger) {
        this(logger, ignored -> { });
    }

    public AiToolAudit(Logger logger, Consumer<AiToolExecutionState> outcomeListener) {
        this.logger = logger;
        this.outcomeListener = outcomeListener;
    }

    public void record(
            AiPreparedToolCall call,
            String confirmation,
            AiToolExecutionState state,
            long durationMillis) {
        try {
            outcomeListener.accept(state);
        } catch (RuntimeException ignored) {
            // Metrics failures must never change the result of a tool invocation.
        }
        try {
            logger.info(() -> "AI tool correlation=" + call.correlationId()
                    + " player=" + call.context().playerId()
                    + " tool=" + call.tool().definition().name()
                    + " risk=" + call.tool().definition().risk()
                    + " confirmation=" + confirmation
                    + " digest=" + call.argumentDigest()
                    + " state=" + state
                    + " duration_ms=" + Math.max(0L, durationMillis));
        } catch (RuntimeException ignored) {
            // Audit output must never change the result of a tool invocation.
        }
    }
}
