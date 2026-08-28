package com.mohistmc.youer.ai;

import com.mohistmc.youer.ai.model.AiProfile;
import com.mohistmc.youer.ai.provider.AiProvider;
import java.time.Duration;
import java.util.Objects;

public record AiRuntime(
        boolean enabled,
        String command,
        String chatFormat,
        int maxHistory,
        int workerThreads,
        int queueCapacity,
        int maxPendingPerPlayer,
        int maxResponseChars,
        Duration historyIdle,
        boolean toolsEnabled,
        int maxToolSteps,
        int maxToolCallsPerTurn,
        int confirmationTimeoutSeconds,
        boolean playerCommandsRequireConfirmation,
        AiProfile profile,
        AiProvider provider) {

    public AiRuntime {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(chatFormat, "chatFormat");
        Objects.requireNonNull(historyIdle, "historyIdle");
    }

    public AiRuntime(
            boolean enabled, String command,
            String chatFormat, int maxHistory, int workerThreads, int queueCapacity,
            AiProfile profile, AiProvider provider) {
        this(enabled, command, chatFormat, maxHistory, workerThreads, queueCapacity,
                2, 16_384, Duration.ofMinutes(30),
                false, 5, 8, 60, true, profile, provider);
    }

    public record Settings(
            boolean enabled,
            String provider,
            String baseUrl,
            String apiKey,
            String model,
            String systemPrompt,
            int maxTokens,
            Duration timeout,
            String apiVersion,
            int maxHistory,
            int workerThreads,
            int queueCapacity,
            int maxPendingPerPlayer,
            int maxResponseChars,
            Duration historyIdle,
            boolean allowInsecureHttp,
            String command,
            String chatFormat,
            boolean toolsEnabled,
            int maxToolSteps,
            int maxToolCallsPerTurn,
            Duration confirmationTimeout,
            boolean playerCommandsRequireConfirmation) {

        public Settings {
            systemPrompt = systemPrompt == null ? "" : systemPrompt;
            apiVersion = apiVersion == null ? "" : apiVersion;
        }

        public Settings withProvider(String value) {
            return copy(value, baseUrl, apiKey, model, maxTokens, timeout, apiVersion, maxHistory,
                    workerThreads, queueCapacity, maxPendingPerPlayer, maxResponseChars, historyIdle,
                    allowInsecureHttp, command, chatFormat, maxToolSteps, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withBaseUrl(String value) {
            return copy(provider, value, apiKey, model, maxTokens, timeout, apiVersion, maxHistory,
                    workerThreads, queueCapacity, maxPendingPerPlayer, maxResponseChars, historyIdle,
                    allowInsecureHttp, command, chatFormat, maxToolSteps, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withApiKey(String value) {
            return copy(provider, baseUrl, value, model, maxTokens, timeout, apiVersion, maxHistory,
                    workerThreads, queueCapacity, maxPendingPerPlayer, maxResponseChars, historyIdle,
                    allowInsecureHttp, command, chatFormat, maxToolSteps, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withModel(String value) {
            return copy(provider, baseUrl, apiKey, value, maxTokens, timeout, apiVersion, maxHistory,
                    workerThreads, queueCapacity, maxPendingPerPlayer, maxResponseChars, historyIdle,
                    allowInsecureHttp, command, chatFormat, maxToolSteps, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withMaxTokens(int value) {
            return copy(provider, baseUrl, apiKey, model, value, timeout, apiVersion, maxHistory,
                    workerThreads, queueCapacity, maxPendingPerPlayer, maxResponseChars, historyIdle,
                    allowInsecureHttp, command, chatFormat, maxToolSteps, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withTimeout(Duration value) {
            return copy(provider, baseUrl, apiKey, model, maxTokens, value, apiVersion, maxHistory,
                    workerThreads, queueCapacity, maxPendingPerPlayer, maxResponseChars, historyIdle,
                    allowInsecureHttp, command, chatFormat, maxToolSteps, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withApiVersion(String value) {
            return copy(provider, baseUrl, apiKey, model, maxTokens, timeout, value, maxHistory,
                    workerThreads, queueCapacity, maxPendingPerPlayer, maxResponseChars, historyIdle,
                    allowInsecureHttp, command, chatFormat, maxToolSteps, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withMaxHistory(int value) {
            return copy(provider, baseUrl, apiKey, model, maxTokens, timeout, apiVersion, value,
                    workerThreads, queueCapacity, maxPendingPerPlayer, maxResponseChars, historyIdle,
                    allowInsecureHttp, command, chatFormat, maxToolSteps, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withWorkerThreads(int value) {
            return copy(provider, baseUrl, apiKey, model, maxTokens, timeout, apiVersion, maxHistory,
                    value, queueCapacity, maxPendingPerPlayer, maxResponseChars, historyIdle,
                    allowInsecureHttp, command, chatFormat, maxToolSteps, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withQueueCapacity(int value) {
            return copy(provider, baseUrl, apiKey, model, maxTokens, timeout, apiVersion, maxHistory,
                    workerThreads, value, maxPendingPerPlayer, maxResponseChars, historyIdle,
                    allowInsecureHttp, command, chatFormat, maxToolSteps, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withMaxPendingPerPlayer(int value) {
            return copy(provider, baseUrl, apiKey, model, maxTokens, timeout, apiVersion, maxHistory,
                    workerThreads, queueCapacity, value, maxResponseChars, historyIdle,
                    allowInsecureHttp, command, chatFormat, maxToolSteps, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withMaxResponseChars(int value) {
            return copy(provider, baseUrl, apiKey, model, maxTokens, timeout, apiVersion, maxHistory,
                    workerThreads, queueCapacity, maxPendingPerPlayer, value, historyIdle,
                    allowInsecureHttp, command, chatFormat, maxToolSteps, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withHistoryIdle(Duration value) {
            return copy(provider, baseUrl, apiKey, model, maxTokens, timeout, apiVersion, maxHistory,
                    workerThreads, queueCapacity, maxPendingPerPlayer, maxResponseChars, value,
                    allowInsecureHttp, command, chatFormat, maxToolSteps, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withAllowInsecureHttp(boolean value) {
            return copy(provider, baseUrl, apiKey, model, maxTokens, timeout, apiVersion, maxHistory,
                    workerThreads, queueCapacity, maxPendingPerPlayer, maxResponseChars, historyIdle,
                    value, command, chatFormat, maxToolSteps, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withCommand(String value) {
            return copy(provider, baseUrl, apiKey, model, maxTokens, timeout, apiVersion, maxHistory,
                    workerThreads, queueCapacity, maxPendingPerPlayer, maxResponseChars, historyIdle,
                    allowInsecureHttp, value, chatFormat, maxToolSteps, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withChatFormat(String value) {
            return copy(provider, baseUrl, apiKey, model, maxTokens, timeout, apiVersion, maxHistory,
                    workerThreads, queueCapacity, maxPendingPerPlayer, maxResponseChars, historyIdle,
                    allowInsecureHttp, command, value, maxToolSteps, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withMaxToolSteps(int value) {
            return copy(provider, baseUrl, apiKey, model, maxTokens, timeout, apiVersion, maxHistory,
                    workerThreads, queueCapacity, maxPendingPerPlayer, maxResponseChars, historyIdle,
                    allowInsecureHttp, command, chatFormat, value, maxToolCallsPerTurn,
                    confirmationTimeout);
        }

        public Settings withMaxToolCallsPerTurn(int value) {
            return copy(provider, baseUrl, apiKey, model, maxTokens, timeout, apiVersion, maxHistory,
                    workerThreads, queueCapacity, maxPendingPerPlayer, maxResponseChars, historyIdle,
                    allowInsecureHttp, command, chatFormat, maxToolSteps, value,
                    confirmationTimeout);
        }

        public Settings withConfirmationTimeout(Duration value) {
            return copy(provider, baseUrl, apiKey, model, maxTokens, timeout, apiVersion, maxHistory,
                    workerThreads, queueCapacity, maxPendingPerPlayer, maxResponseChars, historyIdle,
                    allowInsecureHttp, command, chatFormat, maxToolSteps, maxToolCallsPerTurn, value);
        }

        private Settings copy(
                String newProvider, String newBaseUrl, String newApiKey, String newModel,
                int newMaxTokens, Duration newTimeout, String newApiVersion, int newMaxHistory,
                int newWorkerThreads, int newQueueCapacity, int newMaxPendingPerPlayer,
                int newMaxResponseChars, Duration newHistoryIdle, boolean newAllowInsecureHttp,
                String newCommand, String newChatFormat, int newMaxToolSteps,
                int newMaxToolCallsPerTurn, Duration newConfirmationTimeout) {
            return new Settings(enabled, newProvider, newBaseUrl, newApiKey, newModel, systemPrompt,
                    newMaxTokens, newTimeout, newApiVersion, newMaxHistory, newWorkerThreads,
                    newQueueCapacity, newMaxPendingPerPlayer, newMaxResponseChars, newHistoryIdle,
                    newAllowInsecureHttp, newCommand, newChatFormat, toolsEnabled, newMaxToolSteps,
                    newMaxToolCallsPerTurn, newConfirmationTimeout,
                    playerCommandsRequireConfirmation);
        }

        @Override
        public String toString() {
            return "Settings[enabled=" + enabled + ", provider=" + provider + ", baseUrl=" + baseUrl
                    + ", apiKey=<redacted>, model=" + model + ", maxTokens=" + maxTokens + "]";
        }
    }
}
