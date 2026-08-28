package com.mohistmc.youer.ai.http;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class JdkAiHttpClient implements AiHttpClient, AutoCloseable {

    private static final int DEFAULT_MAX_RESPONSE_BYTES = 1_048_576;
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final HttpClient client;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private final Map<CompletableFuture<AiHttpResponse>, CompletableFuture<?>> exchanges =
            new ConcurrentHashMap<>();

    public JdkAiHttpClient() {
        executor = Executors.newFixedThreadPool(4, runnable -> daemonThread(runnable, "Youer AI HTTP-"));
        scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable -> daemonThread(runnable, "Youer AI HTTP Timeout-"));
        client = HttpClient.newBuilder()
                .executor(executor)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public CompletionStage<AiHttpResponse> execute(AiHttpRequest request, Duration timeout) {
        return execute(request, timeout, DEFAULT_MAX_RESPONSE_BYTES);
    }

    @Override
    public CompletionStage<AiHttpResponse> execute(
            AiHttpRequest request, Duration timeout, int maxResponseBytes) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (closed.get()) {
            return CompletableFuture.failedFuture(closedFailure());
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(request.body(), java.nio.charset.StandardCharsets.UTF_8));
        request.headers().forEach(builder::header);
        CompletableFuture<HttpResponse<String>> exchange;
        try {
            exchange = client.sendAsync(builder.build(), new BoundedStringBodyHandler(maxResponseBytes));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(transportFailure(failure));
        }

        CompletableFuture<AiHttpResponse> result = new CompletableFuture<>();
        ScheduledFuture<?> timeoutTask;
        synchronized (lifecycleLock) {
            if (closed.get()) {
                exchange.cancel(true);
                result.completeExceptionally(closedFailure());
                return result;
            }
            timeoutTask = scheduler.schedule(() -> {
                if (result.completeExceptionally(
                        new AiHttpException(AiHttpException.Reason.TIMEOUT, null))) {
                    exchange.cancel(true);
                }
            }, timeout.toNanos(), TimeUnit.NANOSECONDS);
            exchanges.put(result, exchange);
        }

        exchange.whenComplete((response, failure) -> {
            if (failure != null) {
                result.completeExceptionally(transportFailure(failure));
                return;
            }
            Map<String, String> headers = new LinkedHashMap<>();
            response.headers().map().forEach((name, values) -> {
                if (!values.isEmpty()) {
                    headers.put(name, values.getFirst());
                }
            });
            result.complete(new AiHttpResponse(response.statusCode(), headers, response.body()));
        });
        result.whenComplete((ignored, failure) -> {
            timeoutTask.cancel(false);
            exchanges.remove(result);
        });
        return result;
    }

    @Override
    public void close() {
        Map<CompletableFuture<AiHttpResponse>, CompletableFuture<?>> pending;
        synchronized (lifecycleLock) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            pending = Map.copyOf(exchanges);
            exchanges.clear();
            scheduler.shutdownNow();
            executor.shutdownNow();
        }
        pending.forEach((result, exchange) -> {
            result.completeExceptionally(closedFailure());
            exchange.cancel(true);
        });
    }

    private static Thread daemonThread(Runnable runnable, String prefix) {
        Thread thread = new Thread(runnable, prefix + THREAD_SEQUENCE.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

    private static AiHttpException transportFailure(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof AiHttpException exception) {
            return exception;
        }
        return new AiHttpException(AiHttpException.Reason.TRANSPORT, cause);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static AiHttpException closedFailure() {
        return new AiHttpException(AiHttpException.Reason.CLOSED, null);
    }
}
