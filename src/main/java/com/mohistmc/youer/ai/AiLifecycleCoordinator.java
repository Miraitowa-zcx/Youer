package com.mohistmc.youer.ai;

import com.mohistmc.youer.ai.tool.AiToolRegistry;
import com.mohistmc.youer.ai.tool.confirmation.AiConfirmationStore;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class AiLifecycleCoordinator {

    private final AiToolRegistry registry;
    private final AiConfirmationStore confirmations;
    private final BooleanSupplier enabled;
    private final CandidateFactory candidates;
    private final Consumer<Throwable> failureLogger;
    private final AtomicBoolean bootstrapped = new AtomicBoolean();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicReference<AiLifecycleCandidate> active = new AtomicReference<>();
    private final AtomicReference<AiInitializationStatus> status =
            new AtomicReference<>(AiInitializationStatus.of(AiInitializationStatus.State.NEW));

    AiLifecycleCoordinator(
            AiToolRegistry registry,
            AiConfirmationStore confirmations,
            BooleanSupplier enabled,
            CandidateFactory candidates,
            Consumer<Throwable> failureLogger) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.confirmations = Objects.requireNonNull(confirmations, "confirmations");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.candidates = Objects.requireNonNull(candidates, "candidates");
        this.failureLogger = Objects.requireNonNull(failureLogger, "failureLogger");
    }

    void bootstrap() {
        if (bootstrapped.compareAndSet(false, true)) {
            AiToolRegistry.activate(registry);
            status.set(AiInitializationStatus.of(AiInitializationStatus.State.REGISTRY_READY));
        }
    }

    synchronized void initialize() {
        if (!initialized.compareAndSet(false, true) || stopped.get()) {
            return;
        }
        bootstrap();
        if (!enabled.getAsBoolean()) {
            status.set(AiInitializationStatus.of(AiInitializationStatus.State.DISABLED));
            return;
        }
        AiLifecycleCandidate candidate = null;
        try {
            candidate = Objects.requireNonNull(candidates.create(), "AI lifecycle candidate");
            if (!active.compareAndSet(null, candidate)) {
                candidate.close();
                throw new IllegalStateException("AI runtime is already active");
            }
            status.set(AiInitializationStatus.of(AiInitializationStatus.State.ENABLED));
        } catch (Throwable failure) {
            if (candidate != null && active.get() != candidate) {
                candidate.close();
            }
            status.set(new AiInitializationStatus(
                    AiInitializationStatus.State.FAILED,
                    failure.getClass().getSimpleName(),
                    safeMessage(failure)));
            failureLogger.accept(failure);
        }
    }

    void stopAccepting() {
        AiLifecycleCandidate candidate = active.get();
        if (candidate != null) {
            status.set(AiInitializationStatus.of(AiInitializationStatus.State.STOPPING));
            candidate.retire();
        }
    }

    synchronized void shutdown() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        stopAccepting();
        confirmations.cancelAll();
        AiLifecycleCandidate candidate = active.getAndSet(null);
        if (candidate != null) {
            candidate.close();
        }
        AiToolRegistry.deactivate(registry);
        status.set(AiInitializationStatus.of(AiInitializationStatus.State.STOPPED));
    }

    AiChatService service() {
        AiLifecycleCandidate candidate = active.get();
        return candidate == null ? null : candidate.service();
    }

    AiInitializationStatus status() {
        return status.get();
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    @FunctionalInterface
    interface CandidateFactory {
        AiLifecycleCandidate create() throws Exception;
    }
}
