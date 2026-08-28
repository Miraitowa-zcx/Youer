package com.mohistmc.youer.ai;

import com.mohistmc.youer.ai.admission.AiAdmissionMetrics;
import com.mohistmc.youer.ai.admission.AiRequestAdmissionGate;
import com.mohistmc.youer.ai.history.AiConversationSnapshot;
import com.mohistmc.youer.ai.history.AiConversationStore;
import com.mohistmc.youer.ai.model.AiChatRequest;
import com.mohistmc.youer.ai.model.AiChatResponse;
import com.mohistmc.youer.ai.model.AiMessage;
import com.mohistmc.youer.ai.model.AiRole;
import com.mohistmc.youer.ai.metrics.AiMetrics;
import com.mohistmc.youer.ai.metrics.AiMetricsSnapshot;
import com.mohistmc.youer.ai.provider.AiProvider;
import com.mohistmc.youer.ai.skill.AiCapabilitySnapshot;
import com.mohistmc.youer.ai.skill.AiCapabilitySnapshotProvider;
import com.mohistmc.youer.ai.tool.AiAgentLoop;
import com.mohistmc.youer.ai.tool.AiAgentFailure;
import com.mohistmc.youer.ai.tool.AiAgentRequest;
import com.mohistmc.youer.ai.tool.AiToolRegistry;
import com.mohistmc.youer.ai.tool.AiToolExecutionLedger;
import com.mohistmc.youer.api.ai.tool.AiToolContext;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class AiChatService implements AutoCloseable {

    private final AiRuntime runtime;
    private final AiConversationStore history;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicInteger activeRequests = new AtomicInteger();
    private final ExecutorService executor;
    private final AiRequestAdmissionGate admission;
    private final AiAgentLoop agentLoop;
    private final AiCapabilitySnapshotProvider capabilitySnapshots;
    private final AiMetrics metrics;

    public AiChatService(AiRuntime runtime, AiConversationStore history) {
        this(runtime, history, null, null, new AiMetrics());
    }

    public AiChatService(
            AiRuntime runtime,
            AiConversationStore history,
            AiAgentLoop agentLoop,
            AiCapabilitySnapshotProvider capabilitySnapshots) {
        this(runtime, history, agentLoop, capabilitySnapshots, new AiMetrics());
    }

    public AiChatService(
            AiRuntime runtime,
            AiConversationStore history,
            AiAgentLoop agentLoop,
            AiCapabilitySnapshotProvider capabilitySnapshots,
            AiMetrics metrics) {
        this.runtime = runtime;
        this.history = history;
        this.agentLoop = agentLoop;
        this.capabilitySnapshots = capabilitySnapshots;
        this.metrics = java.util.Objects.requireNonNull(metrics, "metrics");
        if (agentLoop != null && capabilitySnapshots == null) {
            throw new IllegalArgumentException("Agent loop requires capability snapshots");
        }
        this.admission = new AiRequestAdmissionGate(
                runtime.workerThreads(), runtime.queueCapacity(), runtime.maxPendingPerPlayer());
        this.executor = createExecutor(runtime);
    }

    public CompletableFuture<AiChatResponse> chat(AiToolContext context, String message) {
        UUID playerId = context.playerId();
        if (!accepting.get()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("AI chat service is retired"));
        }
        AiRuntime snapshot = runtime;
        if (!snapshot.enabled() || snapshot.profile() == null || snapshot.provider() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("AI chat service is unavailable"));
        }
        CompletableFuture<AiChatResponse> result = new CompletableFuture<>();
        admission.acquire(playerId).whenComplete((permit, admissionFailure) -> {
            if (admissionFailure != null) {
                result.completeExceptionally(admissionFailure);
                return;
            }
            activeRequests.incrementAndGet();
            metrics.recordChatStarted();
            String correlationId = UUID.randomUUID().toString();
            long startedNanos = System.nanoTime();
            CompletionStage<AiChatResponse> invocation = submitStage(() -> {
                long conversationVersion = history.snapshot(playerId).version();
                return invoke(snapshot, context, conversationVersion, message, correlationId);
            });
            invocation.whenComplete((response, failure) -> {
                try {
                    metrics.recordChatCompleted(failure == null,
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
                    if (failure == null) {
                        result.complete(response);
                    } else {
                        result.completeExceptionally(failure);
                    }
                } finally {
                    permit.close();
                    activeRequests.decrementAndGet();
                    shutdownWhenRetiredAndIdle();
                }
            });
        });
        return result;
    }

    public AiRuntime runtime() {
        return runtime;
    }

    public Map<UUID, AiConversationSnapshot> histories() {
        return history.snapshots();
    }

    public void clear(UUID playerId) {
        history.clear(playerId);
    }

    public void clearAll() {
        history.clearAll();
    }

    public AiAdmissionMetrics admissionMetrics() {
        return admission.metrics();
    }

    public AiMetricsSnapshot metrics() {
        return metrics.snapshot();
    }

    public void retire() {
        if (accepting.compareAndSet(true, false)) {
            admission.retire(new RejectedExecutionException("AI chat service is retired"));
        }
        shutdownWhenRetiredAndIdle();
    }

    @Override
    public void close() {
        retire();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 || !executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private java.util.concurrent.CompletionStage<AiChatResponse> invoke(
            AiRuntime snapshot, AiToolContext context, long version, String message,
            String correlationId) {
        UUID playerId = context.playerId();
        ArrayList<AiMessage> messages = new ArrayList<>(history.snapshot(playerId).messages());
        AiMessage userMessage = new AiMessage(AiRole.USER, message);
        messages.add(userMessage);
        if (capabilitySnapshots != null) {
            return capabilitySnapshots.snapshot(context, snapshot.toolsEnabled(), message)
                    .thenCompose(capabilities -> invokeWithCapabilities(
                            snapshot, context, version, userMessage, messages, capabilities,
                            correlationId));
        }
        return invokeWithCapabilities(
                snapshot, context, version, userMessage, messages, null, correlationId);
    }

    private java.util.concurrent.CompletionStage<AiChatResponse> invokeWithCapabilities(
            AiRuntime snapshot,
            AiToolContext context,
            long version,
            AiMessage userMessage,
            ArrayList<AiMessage> messages,
            AiCapabilitySnapshot capabilities,
            String correlationId) {
        UUID playerId = context.playerId();
        AiProvider provider = metrics.observe(
                snapshot.provider(), snapshot.profile().provider(), correlationId);
        String capabilityContext = capabilities == null ? "" : capabilities.systemContext();
        String localeContext = "Player locale: " + context.locale().toLanguageTag()
                + ". Reply in this locale when practical.";
        String systemContext = capabilityContext.isBlank()
                ? localeContext : capabilityContext + "\n\n" + localeContext;
        messages.addFirst(new AiMessage(AiRole.SYSTEM, systemContext));
        if (agentLoop == null) {
            return submitStage(() -> provider.chat(new AiChatRequest(messages)))
                    .thenApply(response -> {
                        history.appendIfVersion(playerId, version, userMessage,
                                new AiMessage(AiRole.ASSISTANT, response.content()), snapshot.maxHistory());
                        return response;
                    });
        }
        AiToolRegistry.Snapshot tools = java.util.Objects.requireNonNull(capabilities).tools();
        return agentLoop.run(new AiAgentRequest(provider, messages, tools, context,
                        snapshot.maxToolSteps(), snapshot.maxToolCallsPerTurn(),
                        new AiToolExecutionLedger(), correlationId), executor)
                .handle((result, failure) -> {
                    if (failure == null) {
                        history.appendIfVersion(playerId, version, result.turn(), snapshot.maxHistory());
                        return result.response();
                    }
                    Throwable cause = unwrap(failure);
                    if (cause instanceof AiAgentFailure agentFailure) {
                        history.appendIfVersion(
                                playerId, version, agentFailure.compactTurn(), snapshot.maxHistory());
                    }
                    if (cause instanceof RuntimeException runtimeFailure) throw runtimeFailure;
                    throw new CompletionException(cause);
                });
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private <T> CompletableFuture<T> submit(Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable command = () -> {
            try {
                result.complete(action.get());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        };
        try {
            executor.execute(command);
        } catch (RejectedExecutionException exception) {
            result.completeExceptionally(exception);
        }
        return result;
    }

    private <T> CompletableFuture<T> submitStage(
            Supplier<? extends java.util.concurrent.CompletionStage<T>> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        CompletableFuture<Void> scheduled = submit(() -> {
            try {
                action.get().whenComplete((value, failure) -> {
                    if (failure == null) result.complete(value);
                    else result.completeExceptionally(failure);
                });
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
            return null;
        });
        scheduled.whenComplete((ignored, failure) -> {
            if (failure != null) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private void shutdownWhenRetiredAndIdle() {
        if (!accepting.get() && activeRequests.get() == 0) {
            executor.shutdown();
        }
    }

    private static ExecutorService createExecutor(AiRuntime runtime) {
        return new ThreadPoolExecutor(
                runtime.workerThreads(),
                runtime.workerThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new AiThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    private static final class AiThreadFactory implements ThreadFactory {
        private static final AtomicInteger SEQUENCE = new AtomicInteger();

        @Override
        public Thread newThread(@NonNull Runnable runnable) {
            Thread thread = new Thread(runnable, "Youer AI Worker-" + SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
