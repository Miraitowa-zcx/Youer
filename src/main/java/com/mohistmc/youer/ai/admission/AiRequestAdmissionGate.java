package com.mohistmc.youer.ai.admission;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AiRequestAdmissionGate {

    private final int maxActive;
    private final int queueCapacity;
    private final int maxPendingPerPlayer;
    private final ArrayDeque<Waiter> waiters = new ArrayDeque<>();
    private final Set<UUID> activePlayers = new HashSet<>();
    private final Map<UUID, Integer> playerCounts = new HashMap<>();
    private int active;
    private long rejected;
    private boolean retired;
    private Throwable retirementCause;

    public AiRequestAdmissionGate(int maxActive, int queueCapacity, int maxPendingPerPlayer) {
        if (maxActive < 1 || queueCapacity < 0 || maxPendingPerPlayer < 1) {
            throw new IllegalArgumentException("AI admission limits are invalid");
        }
        this.maxActive = maxActive;
        this.queueCapacity = queueCapacity;
        this.maxPendingPerPlayer = maxPendingPerPlayer;
    }

    public CompletionStage<Permit> acquire(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        CompletableFuture<Permit> result = new CompletableFuture<>();
        Permit immediate = null;
        synchronized (this) {
            if (retired) {
                rejected++;
                result.completeExceptionally(rejection(AiAdmissionDecision.RETIRED, retirementCause));
                return result;
            }
            int playerCount = playerCounts.getOrDefault(playerId, 0);
            if (playerCount >= maxPendingPerPlayer) {
                rejected++;
                result.completeExceptionally(rejection(AiAdmissionDecision.REJECTED_PLAYER_LIMIT, null));
                return result;
            }
            if (active < maxActive && !activePlayers.contains(playerId)) {
                incrementPlayer(playerId);
                active++;
                activePlayers.add(playerId);
                immediate = new GatePermit(playerId);
            } else if (waiters.size() >= queueCapacity) {
                rejected++;
                result.completeExceptionally(rejection(AiAdmissionDecision.REJECTED_GLOBAL_LIMIT, null));
                return result;
            } else {
                incrementPlayer(playerId);
                waiters.addLast(new Waiter(playerId, result));
            }
        }
        if (immediate != null) {
            result.complete(immediate);
        }
        return result;
    }

    public void retire(Throwable cause) {
        List<Waiter> failed;
        synchronized (this) {
            if (retired) {
                return;
            }
            retired = true;
            retirementCause = cause;
            failed = new ArrayList<>(waiters);
            waiters.clear();
            for (Waiter waiter : failed) {
                decrementPlayer(waiter.playerId());
            }
        }
        RejectedExecutionException rejection = rejection(AiAdmissionDecision.RETIRED, cause);
        failed.forEach(waiter -> waiter.future().completeExceptionally(rejection));
    }

    public synchronized AiAdmissionMetrics metrics() {
        return new AiAdmissionMetrics(active, waiters.size(), rejected);
    }

    private void release(UUID playerId) {
        List<Activation> activations;
        synchronized (this) {
            if (!activePlayers.remove(playerId)) {
                return;
            }
            active--;
            decrementPlayer(playerId);
            activations = retired ? List.of() : promote();
        }
        activations.forEach(activation -> activation.future().complete(activation.permit()));
    }

    private List<Activation> promote() {
        List<Activation> activations = new ArrayList<>();
        while (active < maxActive) {
            Waiter selected = null;
            Iterator<Waiter> iterator = waiters.iterator();
            while (iterator.hasNext()) {
                Waiter candidate = iterator.next();
                if (!activePlayers.contains(candidate.playerId())) {
                    selected = candidate;
                    iterator.remove();
                    break;
                }
            }
            if (selected == null) {
                break;
            }
            active++;
            activePlayers.add(selected.playerId());
            activations.add(new Activation(
                    selected.future(), new GatePermit(selected.playerId())));
        }
        return activations;
    }

    private void incrementPlayer(UUID playerId) {
        playerCounts.merge(playerId, 1, Integer::sum);
    }

    private void decrementPlayer(UUID playerId) {
        int remaining = playerCounts.getOrDefault(playerId, 0) - 1;
        if (remaining <= 0) {
            playerCounts.remove(playerId);
        } else {
            playerCounts.put(playerId, remaining);
        }
    }

    private static RejectedExecutionException rejection(AiAdmissionDecision decision, Throwable cause) {
        RejectedExecutionException exception = new RejectedExecutionException(
                "AI request admission rejected: " + decision.name().toLowerCase(java.util.Locale.ROOT));
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    public interface Permit extends AutoCloseable {
        UUID playerId();

        @Override
        void close();
    }

    private final class GatePermit implements Permit {
        private final UUID playerId;
        private final AtomicBoolean closed = new AtomicBoolean();

        private GatePermit(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public UUID playerId() {
            return playerId;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release(playerId);
            }
        }
    }

    private record Waiter(UUID playerId, CompletableFuture<Permit> future) {
    }

    private record Activation(CompletableFuture<Permit> future, Permit permit) {
    }
}
