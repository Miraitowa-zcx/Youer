package com.mohistmc.youer.ai.metrics;

import com.mohistmc.youer.ai.tool.AiToolExecutionState;
import java.util.Map;

public record AiMetricsSnapshot(
        long chatsStarted,
        long chatsSucceeded,
        long chatsFailed,
        long chatLatencyMillis,
        long providerRequests,
        long providerFailures,
        long providerLatencyMillis,
        long inputTokens,
        long outputTokens,
        String lastReturnedModel,
        String lastFinishCategory,
        Map<AiToolExecutionState, Long> toolOutcomes) {

    public AiMetricsSnapshot {
        lastReturnedModel = lastReturnedModel == null ? "" : lastReturnedModel;
        lastFinishCategory = lastFinishCategory == null ? "" : lastFinishCategory;
        toolOutcomes = Map.copyOf(toolOutcomes);
    }
}
