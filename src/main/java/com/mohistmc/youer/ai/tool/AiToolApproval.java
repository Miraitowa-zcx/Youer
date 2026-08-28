package com.mohistmc.youer.ai.tool;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface AiToolApproval {
    CompletionStage<AiToolApprovalDecision> request(AiPreparedToolCall call);
}
