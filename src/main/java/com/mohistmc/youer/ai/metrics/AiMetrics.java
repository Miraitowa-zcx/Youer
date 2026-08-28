package com.mohistmc.youer.ai.metrics;

import com.mohistmc.youer.ai.error.AiErrorType;
import com.mohistmc.youer.ai.error.AiProviderException;
import com.mohistmc.youer.ai.model.AiChatRequest;
import com.mohistmc.youer.ai.model.AiChatResponse;
import com.mohistmc.youer.ai.model.AiTokenUsage;
import com.mohistmc.youer.ai.provider.AiProvider;
import com.mohistmc.youer.ai.provider.AiProviderCapabilities;
import com.mohistmc.youer.ai.tool.AiToolExecutionState;
import java.util.EnumMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class AiMetrics {

    private static final Logger LOGGER = LogManager.getLogger(AiMetrics.class);

    private final LongAdder chatsStarted = new LongAdder();
    private final LongAdder chatsSucceeded = new LongAdder();
    private final LongAdder chatsFailed = new LongAdder();
    private final LongAdder chatLatencyMillis = new LongAdder();
    private final LongAdder providerRequests = new LongAdder();
    private final LongAdder providerFailures = new LongAdder();
    private final LongAdder providerLatencyMillis = new LongAdder();
    private final LongAdder inputTokens = new LongAdder();
    private final LongAdder outputTokens = new LongAdder();
    private final AtomicReference<String> lastReturnedModel = new AtomicReference<>("");
    private final AtomicReference<String> lastFinishCategory = new AtomicReference<>("");
    private final EnumMap<AiToolExecutionState, LongAdder> toolOutcomes =
            new EnumMap<>(AiToolExecutionState.class);

    public AiMetrics() {
        for (AiToolExecutionState state : AiToolExecutionState.values()) {
            toolOutcomes.put(state, new LongAdder());
        }
    }

    public void recordChatStarted() {
        chatsStarted.increment();
    }

    public void recordChatCompleted(boolean success, long durationMillis) {
        (success ? chatsSucceeded : chatsFailed).increment();
        chatLatencyMillis.add(Math.max(0L, durationMillis));
    }

    public void recordToolOutcome(AiToolExecutionState state) {
        toolOutcomes.get(state).increment();
    }

    public AiProvider observe(AiProvider delegate, String providerName, String correlationId) {
        return new AiProvider() {
            @Override
            public CompletionStage<AiChatResponse> chat(AiChatRequest request) {
                providerRequests.increment();
                long started = System.nanoTime();
                try {
                    AiChatRequest correlated = request.correlationId().isBlank()
                            ? request.withCorrelationId(correlationId) : request;
                    return delegate.chat(correlated).whenComplete((response, failure) -> {
                        long duration = elapsedMillis(started);
                        providerLatencyMillis.add(duration);
                        if (failure == null) {
                            recordUsage(response.usage());
                            lastReturnedModel.set(response.model() == null ? "" : response.model());
                            lastFinishCategory.set(response.finishReason() == null ? "" : response.finishReason());
                            LOGGER.info(
                                    "AI provider completed: correlation={}, provider={}, model={}, duration_ms={}, finish={}, input_tokens={}, output_tokens={}",
                                    correlationId, providerName, safe(response.model()), duration,
                                    safe(response.finishReason()), token(response.usage(), true),
                                    token(response.usage(), false));
                        } else {
                            providerFailures.increment();
                            logFailure(correlationId, providerName, duration, unwrap(failure));
                        }
                    });
                } catch (Throwable failure) {
                    long duration = elapsedMillis(started);
                    providerFailures.increment();
                    providerLatencyMillis.add(duration);
                    logFailure(correlationId, providerName, duration, failure);
                    throw failure;
                }
            }

            @Override
            public AiProviderCapabilities capabilities() {
                return delegate.capabilities();
            }
        };
    }

    public AiMetricsSnapshot snapshot() {
        EnumMap<AiToolExecutionState, Long> outcomes = new EnumMap<>(AiToolExecutionState.class);
        toolOutcomes.forEach((state, counter) -> outcomes.put(state, counter.sum()));
        return new AiMetricsSnapshot(
                chatsStarted.sum(), chatsSucceeded.sum(), chatsFailed.sum(), chatLatencyMillis.sum(),
                providerRequests.sum(), providerFailures.sum(), providerLatencyMillis.sum(),
                inputTokens.sum(), outputTokens.sum(), lastReturnedModel.get(),
                lastFinishCategory.get(), outcomes);
    }

    private void recordUsage(AiTokenUsage usage) {
        if (usage == null) return;
        if (usage.inputTokens() != null && usage.inputTokens() > 0) {
            inputTokens.add(usage.inputTokens());
        }
        if (usage.outputTokens() != null && usage.outputTokens() > 0) {
            outputTokens.add(usage.outputTokens());
        }
    }

    private static void logFailure(
            String correlationId, String providerName, long durationMillis, Throwable failure) {
        if (failure instanceof AiProviderException providerFailure
                && concise(providerFailure.type())) {
            LOGGER.warn(
                    "AI provider failed: correlation={}, provider={}, duration_ms={}, category={}, status={}, request_id={}",
                    correlationId, providerName, durationMillis, providerFailure.type(),
                    providerFailure.status(), safe(providerFailure.requestId()));
            return;
        }
        LOGGER.error("AI provider failed: correlation={}, provider={}, duration_ms={}, category={}",
                correlationId, providerName, durationMillis, failure.getClass().getSimpleName(), failure);
    }

    private static boolean concise(AiErrorType type) {
        return type == AiErrorType.AUTHENTICATION || type == AiErrorType.RATE_LIMIT
                || type == AiErrorType.TIMEOUT || type == AiErrorType.INVALID_RESPONSE
                || type == AiErrorType.EMPTY_RESPONSE;
    }

    private static int token(AiTokenUsage usage, boolean input) {
        if (usage == null) return 0;
        Integer value = input ? usage.inputTokens() : usage.outputTokens();
        return value == null ? 0 : Math.max(0, value);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
