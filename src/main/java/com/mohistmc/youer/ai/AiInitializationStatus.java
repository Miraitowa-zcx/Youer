package com.mohistmc.youer.ai;

public record AiInitializationStatus(State state, String errorCategory, String errorMessage) {

    public AiInitializationStatus {
        errorCategory = errorCategory == null ? "" : errorCategory;
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static AiInitializationStatus of(State state) {
        return new AiInitializationStatus(state, "", "");
    }

    public enum State {
        NEW,
        REGISTRY_READY,
        DISABLED,
        ENABLED,
        FAILED,
        STOPPING,
        STOPPED
    }
}
