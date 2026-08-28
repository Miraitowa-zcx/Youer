package com.mohistmc.youer.ai;

import com.mohistmc.youer.YouerConfig;
import com.mohistmc.youer.ai.config.AiConfigValidator;
import com.mohistmc.youer.ai.http.AiHttpClient;
import com.mohistmc.youer.ai.model.AiProfile;
import com.mohistmc.youer.ai.provider.AiProvider;
import com.mohistmc.youer.ai.provider.AiProviderFactory;
import java.time.Duration;

public final class AiRuntimeFactory {

    private AiRuntimeFactory() {
    }

    public static AiRuntime createFromConfig(AiHttpClient httpClient) {
        AiRuntime.Settings settings = new AiConfigValidator().validate(new AiRuntime.Settings(
                YouerConfig.ai_enable,
                YouerConfig.ai_provider,
                YouerConfig.ai_baseUrl,
                YouerConfig.ai_api_key,
                YouerConfig.ai_model,
                YouerConfig.ai_system_prompt,
                YouerConfig.ai_max_tokens,
                Duration.ofSeconds(YouerConfig.ai_timeout_seconds),
                YouerConfig.ai_api_version,
                YouerConfig.ai_max_history,
                YouerConfig.ai_worker_threads,
                YouerConfig.ai_queue_capacity,
                YouerConfig.ai_max_pending_per_player,
                YouerConfig.ai_max_response_chars,
                Duration.ofMinutes(YouerConfig.ai_history_idle_minutes),
                YouerConfig.ai_allow_insecure_http,
                YouerConfig.ai_command,
                YouerConfig.ai_chat_format,
                YouerConfig.ai_tools_enable,
                YouerConfig.ai_tools_max_steps,
                YouerConfig.ai_tools_max_calls_per_turn,
                Duration.ofSeconds(YouerConfig.ai_tools_confirmation_timeout_seconds),
                YouerConfig.ai_tools_player_commands_require_confirmation));
        AiProfile profile = new AiProfile(
                settings.provider(),
                settings.baseUrl(),
                settings.apiKey(),
                settings.model(),
                settings.systemPrompt(),
                settings.maxTokens(),
                settings.timeout(),
                settings.apiVersion());
        AiProvider provider = AiProviderFactory.create(profile, httpClient);
        return new AiRuntime(
                settings.enabled(),
                settings.command(),
                settings.chatFormat(),
                settings.maxHistory(),
                settings.workerThreads(),
                settings.queueCapacity(),
                settings.maxPendingPerPlayer(),
                settings.maxResponseChars(),
                settings.historyIdle(),
                settings.toolsEnabled(),
                settings.maxToolSteps(),
                settings.maxToolCallsPerTurn(),
                Math.toIntExact(settings.confirmationTimeout().toSeconds()),
                settings.playerCommandsRequireConfirmation(),
                profile,
                provider);
    }
}
