package com.mohistmc.youer.ai;

import com.mohistmc.youer.ai.error.AiProviderException;
import com.mohistmc.youer.ai.model.AiChatResponse;
import com.mohistmc.youer.api.ai.tool.AiToolContext;
import com.mohistmc.youer.util.I18n;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.mohistmc.youer.ai.tool.AiToolRegistry;
import com.mohistmc.youer.ai.tool.AiAgentFailure;
import java.util.UUID;
import java.util.Set;

import net.kyori.adventure.text.Component;

public final class AiChatHandler {

    private static final Logger LOGGER = LogManager.getLogger(AiChatHandler.class);
    private static final AiResponseFormatter RESPONSE_FORMATTER = new AiResponseFormatter();
    private AiChatHandler() {
    }

    public static boolean handle(Player player, String rawMessage) {
        AiChatService current = AiLifecycle.service();
        if (current == null) return false;
        AiRuntime runtime = current.runtime();
        if (!runtime.enabled() || !hasChatPermission(player)) {
            return false;
        }
        AiChatInput input = AiChatInput.parse(rawMessage, runtime.command()).orElse(null);
        if (input == null) {
            return false;
        }

        AiResponseAudience responseAudience;
        boolean intercepted;
        if (input.mode() == AiChatInput.Mode.BROADCAST) {
            responseAudience = new AiResponseAudience(Set.copyOf(Bukkit.getOnlinePlayers()));
            intercepted = false;
        } else {
            responseAudience = new AiResponseAudience(Set.of(player));
            intercepted = true;
            dispatch(() -> player.sendMessage(Component.text("<" + player.getName() + "> " + rawMessage)));
        }
        current.chat(new AiToolContext(player.getUniqueId(), player.getName(), player.locale()), input.message())
                .whenComplete((response, failure) ->
                dispatch(() -> complete(player, responseAudience, runtime, response, failure)));
        return intercepted;
    }

    public static AiChatService service() {
        return AiLifecycle.service();
    }

    public static boolean confirm(UUID playerId, String id) { return AiLifecycle.confirm(playerId, id); }
    public static boolean cancel(UUID playerId, String id) { return AiLifecycle.cancel(playerId, id); }
    public static AiToolRegistry.Snapshot tools(Player player) {
        return AiLifecycle.tools(player);
    }

    private static boolean hasChatPermission(Player player) {
        return player.hasPermission("youer.ai.use");
    }

    private static void complete(
            Player player,
            AiResponseAudience audience,
            AiRuntime runtime,
            AiChatResponse response,
            Throwable failure) {
        if (failure == null) {
            try {
                audience.send(RESPONSE_FORMATTER.format(
                        runtime.chatFormat(), response.content(), runtime.maxResponseChars()));
            } catch (RuntimeException exception) {
                LOGGER.error("AI response formatting failed", exception);
                if (player.isOnline()) {
                    player.sendMessage(I18n.as("ai.error.invalid_response"));
                }
            }
            return;
        }

        Throwable cause = unwrap(failure);
        if (player.isOnline()) {
            player.sendMessage(localizedFailure(cause));
        }
        if (cause instanceof AiProviderException providerError) {
            LOGGER.error(
                    "AI request failed: provider={}, status={}, requestId={}, error={}",
                    providerError.provider(),
                    providerError.status(),
                    providerError.requestId(),
                    providerError.getMessage());
        } else {
            LOGGER.error("AI request failed: {}", cause.getClass().getSimpleName());
        }
    }

    private static String localizedFailure(Throwable cause) {
        if (cause instanceof AiAgentFailure failure && failure.actionsMayHaveCompleted()) {
            return I18n.as("ai.error.actions_may_have_completed");
        }
        if (cause instanceof RejectedExecutionException) {
            return I18n.as("ai.busy");
        }
        if (cause instanceof IllegalStateException) {
            return I18n.as("ai.unavailable");
        }
        if (cause instanceof AiProviderException error) {
            return switch (error.type()) {
                case AUTHENTICATION -> I18n.as("ai.error.authentication");
                case RATE_LIMIT -> I18n.as("ai.error.rate_limit");
                case TIMEOUT -> I18n.as("ai.error.timeout");
                case INVALID_RESPONSE, EMPTY_RESPONSE -> I18n.as("ai.error.invalid_response");
                default -> I18n.as("ai.error.request_failed");
            };
        }
        return I18n.as("ai.error.request_failed");
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while (!(cause instanceof AiAgentFailure)
                && cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    static void dispatch(Runnable action) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            server.execute(action);
        } else {
            action.run();
        }
    }
}
