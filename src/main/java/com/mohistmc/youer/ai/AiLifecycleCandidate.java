package com.mohistmc.youer.ai;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class AiLifecycleCandidate implements AutoCloseable {

    private final AiChatService service;
    private final List<AutoCloseable> resources;
    private final AtomicBoolean closed = new AtomicBoolean();

    AiLifecycleCandidate(AiChatService service, List<? extends AutoCloseable> resources) {
        this.service = Objects.requireNonNull(service, "service");
        this.resources = List.copyOf(resources);
    }

    AiChatService service() {
        return service;
    }

    void retire() {
        service.retire();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        service.clearAll();
        service.close();
        for (int index = resources.size() - 1; index >= 0; index--) {
            try {
                resources.get(index).close();
            } catch (Exception ignored) {
                // Shutdown continues so one auxiliary resource cannot leak the rest.
            }
        }
    }
}
