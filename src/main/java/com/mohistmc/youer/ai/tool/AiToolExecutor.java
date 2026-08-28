package com.mohistmc.youer.ai.tool;

import com.mohistmc.youer.ai.model.AiToolCallContent;
import com.mohistmc.youer.ai.model.AiToolResultContent;
import com.mohistmc.youer.api.ai.tool.AiToolContext;
import com.mohistmc.youer.api.ai.tool.AiToolResult;
import com.mohistmc.youer.util.I18n;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class AiToolExecutor implements AutoCloseable {
    public static final int MAX_RESULT_CHARS = 16_384;

    private final AiToolSchemaValidator validator;
    private final AiToolApproval approval;
    private final AiExecutionDispatcher dispatcher;
    private final BiPredicate<AiToolContext, String> permissionCheck;
    private final Predicate<AiToolContext> onlineCheck;
    private final AiToolAudit audit;
    private final AiToolCallPreparer preparer;
    private final ScheduledExecutorService scheduler;

    public AiToolExecutor(
            AiToolSchemaValidator validator,
            AiToolApproval approval,
            AiExecutionDispatcher dispatcher,
            BiPredicate<AiToolContext, String> permissionCheck,
            Predicate<AiToolContext> onlineCheck) {
        this(validator, approval, dispatcher, permissionCheck, onlineCheck,
                new AiToolAudit(java.util.logging.Logger.getLogger("Youer AI Tools")));
    }

    public AiToolExecutor(
            AiToolSchemaValidator validator,
            AiToolApproval approval,
            AiExecutionDispatcher dispatcher,
            BiPredicate<AiToolContext, String> permissionCheck,
            Predicate<AiToolContext> onlineCheck,
            AiToolAudit audit) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.approval = Objects.requireNonNull(approval, "approval");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.permissionCheck = Objects.requireNonNull(permissionCheck, "permissionCheck");
        this.onlineCheck = Objects.requireNonNull(onlineCheck, "onlineCheck");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.preparer = new AiToolCallPreparer(validator);
        this.scheduler = createScheduler();
    }

    public CompletionStage<AiToolResultContent> execute(
            AiToolContext context, AiRegisteredTool tool, AiToolCallContent call) {
        return execute(context, tool, call, new AiToolExecutionLedger(), UUID.randomUUID().toString());
    }

    public CompletionStage<AiToolResultContent> execute(
            AiToolContext context, AiRegisteredTool tool, AiToolCallContent call,
            AiToolExecutionLedger ledger) {
        return execute(context, tool, call, ledger, UUID.randomUUID().toString());
    }

    public CompletionStage<AiToolResultContent> execute(
            AiToolContext context, AiRegisteredTool tool, AiToolCallContent call,
            AiToolExecutionLedger ledger, String correlationId) {
        return executeChecked(context, tool, call, ledger, correlationId, System.nanoTime());
    }

    private CompletionStage<AiToolResultContent> executeChecked(
            AiToolContext context, AiRegisteredTool tool, AiToolCallContent call,
            AiToolExecutionLedger ledger, String correlationId, long startedNanos) {
        String invalid = invalidReason(context, tool, call);
        if (invalid != null) return CompletableFuture.completedFuture(error(call, invalid));
        AiPreparedToolCall prepared;
        try {
            prepared = preparer.prepare(
                    context, tool, call, correlationId, Instant.now());
        } catch (IllegalArgumentException failure) {
            return CompletableFuture.completedFuture(
                    error(call, I18n.as("ai.tool.error.schema", failure.getMessage())));
        }
        return approval.request(prepared).thenCompose(decision -> {
            if (decision != AiToolApprovalDecision.APPROVED) {
                audit.record(prepared, decision.name().toLowerCase(java.util.Locale.ROOT),
                        AiToolExecutionState.FAILURE, elapsedMillis(startedNanos));
                return CompletableFuture.completedFuture(error(call, approvalError(decision)));
            }
            String rechecked = invalidPreparedReason(prepared);
            if (rechecked != null) return CompletableFuture.completedFuture(error(call, rechecked));
            return dispatcher.dispatch(
                            tool.definition().executionMode(), prepared.deadline(), Clock.systemUTC(), () -> {
                                String insideExecutor = invalidPreparedReason(prepared);
                                if (insideExecutor != null) {
                                    return CompletableFuture.failedFuture(
                                            new PreparedCallRejectedException(insideExecutor));
                                }
                                return tool.handler().execute(context, prepared.arguments());
                            })
                    .thenCompose(outcome -> {
                        if (outcome.state() == AiToolExecutionState.EXPIRED_BEFORE_START) {
                            audit.record(prepared, "approved",
                                    AiToolExecutionState.EXPIRED_BEFORE_START, elapsedMillis(startedNanos));
                            return CompletableFuture.completedFuture(
                                    error(call, I18n.as("ai.tool.error.expired")));
                        }
                        ledger.recordStarted(tool);
                        return awaitStartedCall(
                                prepared, call, outcome.completion(), "approved", startedNanos);
                    });
        });
    }

    private String invalidPreparedReason(AiPreparedToolCall prepared) {
        AiToolContext context = prepared.context();
        AiRegisteredTool tool = prepared.tool();
        if (Instant.now().isAfter(prepared.deadline())) return I18n.as("ai.tool.error.expired");
        if (!onlineCheck.test(context)) return I18n.as("ai.tool.error.player_unavailable");
        if (!tool.owner().isEnabled()) return I18n.as("ai.tool.error.unavailable");
        if (!permissionCheck.test(context, tool.definition().permission())) {
            return I18n.as("ai.tool.error.permission_revoked");
        }
        return null;
    }

    private String invalidReason(AiToolContext context, AiRegisteredTool tool, AiToolCallContent call) {
        if (!onlineCheck.test(context)) return I18n.as("ai.tool.error.player_unavailable");
        if (!tool.owner().isEnabled()) return I18n.as("ai.tool.error.unavailable");
        if (!permissionCheck.test(context, tool.definition().permission())) {
            return I18n.as("ai.tool.error.permission_revoked");
        }
        return tool.definition().name().equals(call.name())
                ? null : I18n.as("ai.tool.error.unavailable");
    }

    private static String approvalError(AiToolApprovalDecision decision) {
        return I18n.as(switch (decision) {
            case DENIED -> "ai.tool.error.denied";
            case EXPIRED -> "ai.tool.error.expired";
            case CANCELLED -> "ai.tool.error.cancelled";
            case UNAVAILABLE -> "ai.tool.error.unavailable";
            case APPROVED -> throw new IllegalArgumentException("Approved is not an error");
        });
    }

    private static AiToolResultContent content(AiToolCallContent call, AiToolResult result) {
        return new AiToolResultContent(
                call.id(), call.name(), truncate(result.content()), result.error(), call.attributes());
    }

    private static AiToolResultContent error(AiToolCallContent call, String message) {
        return new AiToolResultContent(
                call.id(), call.name(), truncate(message), true, call.attributes());
    }

    private static String truncate(String value) {
        return value.length() <= MAX_RESULT_CHARS ? value : value.substring(0, MAX_RESULT_CHARS);
    }

    private CompletionStage<AiToolResultContent> awaitStartedCall(
            AiPreparedToolCall prepared,
            AiToolCallContent call,
            CompletionStage<AiToolResult> underlying,
            String confirmation,
            long startedNanos) {
        CompletableFuture<AiToolResultContent> terminal = new CompletableFuture<>();
        AtomicBoolean terminalClaimed = new AtomicBoolean();
        long remainingMillis = Math.max(1L,
                Duration.between(Instant.now(), prepared.deadline()).toMillis());
        ScheduledFuture<?> timeout = scheduler.schedule(() -> {
            boolean uncertain = prepared.tool().definition().risk()
                    != com.mohistmc.youer.api.ai.tool.AiToolRisk.READ_ONLY;
            if (terminalClaimed.compareAndSet(false, true)) {
                audit.record(prepared, confirmation, uncertain
                                ? AiToolExecutionState.TIMED_OUT_STATE_UNKNOWN
                                : AiToolExecutionState.TIMEOUT,
                        elapsedMillis(startedNanos));
                terminal.complete(error(call, I18n.as(uncertain
                        ? "ai.tool.error.timeout_state_unknown" : "ai.tool.error.timeout")));
            }
        }, remainingMillis, TimeUnit.MILLISECONDS);
        underlying.whenComplete((result, failure) -> {
            Throwable cause = unwrap(failure);
            AiToolResultContent completed;
            if (cause == null) {
                completed = content(call, result);
            } else if (cause instanceof PreparedCallRejectedException rejected) {
                completed = error(call, rejected.getMessage());
            } else {
                completed = error(call, I18n.as("ai.tool.error.execution_failed"));
            }
            if (terminalClaimed.compareAndSet(false, true)) {
                timeout.cancel(false);
                audit.record(prepared, confirmation,
                        cause == null && !completed.error()
                                ? AiToolExecutionState.SUCCESS : AiToolExecutionState.FAILURE,
                        elapsedMillis(startedNanos));
                terminal.complete(completed);
            } else {
                audit.record(prepared, confirmation,
                        cause == null ? AiToolExecutionState.LATE_SUCCESS
                                : AiToolExecutionState.LATE_FAILURE,
                        elapsedMillis(startedNanos));
            }
        });
        return terminal;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    private static ScheduledExecutorService createScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Youer AI Tool Timeout");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof java.util.concurrent.CompletionException
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class PreparedCallRejectedException extends RuntimeException {
        private PreparedCallRejectedException(String message) {
            super(message);
        }
    }
}
