package com.mohistmc.youer.ai.tool;

import com.mohistmc.youer.ai.history.AiConversationTurn;

public final class AiAgentFailure extends RuntimeException {
    private final boolean actionsMayHaveCompleted;
    private final AiConversationTurn compactTurn;

    public AiAgentFailure(boolean actionsMayHaveCompleted, Throwable cause, AiConversationTurn compactTurn) {
        super(actionsMayHaveCompleted
                ? "AI request failed after Tool actions may have completed; verify server state before retrying"
                : "AI request failed", cause);
        this.actionsMayHaveCompleted = actionsMayHaveCompleted;
        this.compactTurn = compactTurn;
    }

    public boolean actionsMayHaveCompleted() { return actionsMayHaveCompleted; }
    public AiConversationTurn compactTurn() { return compactTurn; }
}
