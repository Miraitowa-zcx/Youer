package com.mohistmc.youer.ai.tool;

import java.util.concurrent.atomic.AtomicBoolean;

public final class AiToolExecutionLedger {
    private final AtomicBoolean mutatingActionStarted = new AtomicBoolean();

    public void recordStarted(AiRegisteredTool tool) {
        if (tool.definition().risk() != com.mohistmc.youer.api.ai.tool.AiToolRisk.READ_ONLY) {
            mutatingActionStarted.set(true);
        }
    }

    public boolean actionsMayHaveCompleted() {
        return mutatingActionStarted.get();
    }
}
