package com.mohistmc.youer.ai.http;

public final class AiHttpException extends RuntimeException {

    public enum Reason {
        TIMEOUT,
        RESPONSE_TOO_LARGE,
        TRANSPORT,
        CLOSED
    }

    private final Reason reason;

    public AiHttpException(boolean timeout, Throwable cause) {
        this(timeout ? Reason.TIMEOUT : Reason.TRANSPORT, cause);
    }

    public AiHttpException(Reason reason, Throwable cause) {
        super(message(reason), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public boolean timeout() {
        return reason == Reason.TIMEOUT;
    }

    public boolean responseTooLarge() {
        return reason == Reason.RESPONSE_TOO_LARGE;
    }

    public boolean closed() {
        return reason == Reason.CLOSED;
    }

    private static String message(Reason reason) {
        return switch (reason) {
            case TIMEOUT -> "AI HTTP request timed out";
            case RESPONSE_TOO_LARGE -> "AI HTTP response exceeded the configured byte limit";
            case TRANSPORT -> "AI HTTP request failed";
            case CLOSED -> "AI HTTP client is closed";
        };
    }
}
