package com.mohistmc.youer.ai.tool;

public enum AiToolExecutionState {
    EXPIRED_BEFORE_START,
    STARTED,
    TIMEOUT,
    TIMED_OUT_STATE_UNKNOWN,
    SUCCESS,
    FAILURE,
    LATE_SUCCESS,
    LATE_FAILURE
}
