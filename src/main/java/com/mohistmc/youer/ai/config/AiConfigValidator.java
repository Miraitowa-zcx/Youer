package com.mohistmc.youer.ai.config;

import com.mohistmc.youer.ai.AiRuntime;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

public final class AiConfigValidator {

    private static final Set<String> PROVIDERS = Set.of(
            "openai-compatible", "deepseek", "openai", "qwen", "kimi", "groq",
            "ollama", "vllm", "anthropic", "gemini");
    private static final Set<String> KEY_OPTIONAL = Set.of("openai-compatible", "ollama", "vllm");
    private final AiSecretResolver secrets;

    public AiConfigValidator() {
        this(System::getenv);
    }

    public AiConfigValidator(Function<String, String> environment) {
        this.secrets = new AiSecretResolver(environment);
    }

    public AiRuntime.Settings validate(AiRuntime.Settings value) {
        String provider = required(value.provider(), "provider").toLowerCase(Locale.ROOT);
        if (!PROVIDERS.contains(provider)) {
            throw invalid("provider", "is unsupported");
        }
        String baseUrl = required(value.baseUrl(), "baseUrl");
        URI uri = parseBaseUrl(baseUrl);
        validateTransport(uri, value.allowInsecureHttp());
        String model = required(value.model(), "model");
        String apiKey = secrets.resolve(value.apiKey());
        if (!KEY_OPTIONAL.contains(provider)
                && (apiKey.isBlank() || "youer".equalsIgnoreCase(apiKey.strip()))) {
            throw invalid("apiKey", "is required for hosted provider " + provider);
        }
        String apiVersion = value.apiVersion() == null ? "" : value.apiVersion().strip();
        if ("anthropic".equals(provider) && apiVersion.isBlank()) {
            throw invalid("apiVersion", "is required for anthropic");
        }
        if ("gemini".equals(provider) && occurrences(baseUrl, "{model}") != 1) {
            throw invalid("baseUrl", "must contain exactly one {model} placeholder for gemini");
        }
        String command = normalizeCommand(value.command());
        validateChatFormat(value.chatFormat());
        positive(value.maxTokens(), 1_000_000, "maxTokens");
        positive(value.timeout(), Duration.ofMinutes(10), "timeout");
        bounded(value.maxHistory(), 2, 4_096, "maxHistory");
        bounded(value.workerThreads(), 1, 64, "workerThreads");
        bounded(value.queueCapacity(), 0, 100_000, "queueCapacity");
        bounded(value.maxPendingPerPlayer(), 1, 128, "maxPendingPerPlayer");
        bounded(value.maxResponseChars(), 1, 1_000_000, "maxResponseChars");
        positive(value.historyIdle(), Duration.ofDays(30), "historyIdle");
        bounded(value.maxToolSteps(), 1, 64, "maxToolSteps");
        bounded(value.maxToolCallsPerTurn(), 1, 256, "maxToolCallsPerTurn");
        positive(value.confirmationTimeout(), Duration.ofMinutes(30), "confirmationTimeout");
        return new AiRuntime.Settings(
                value.enabled(), provider, baseUrl, apiKey, model, value.systemPrompt(),
                value.maxTokens(), value.timeout(), apiVersion, value.maxHistory(),
                value.workerThreads(), value.queueCapacity(), value.maxPendingPerPlayer(),
                value.maxResponseChars(), value.historyIdle(), value.allowInsecureHttp(), command,
                value.chatFormat(), value.toolsEnabled(), value.maxToolSteps(),
                value.maxToolCallsPerTurn(), value.confirmationTimeout(),
                value.playerCommandsRequireConfirmation());
    }

    private static URI parseBaseUrl(String value) {
        try {
            URI uri = new URI(value.replace("{model}", "model"));
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw invalid("baseUrl", "must be an absolute HTTP(S) URL without user-info or fragment");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new AiConfigException("baseUrl is invalid", exception);
        }
    }

    private static void validateTransport(URI uri, boolean allowInsecureHttp) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            return;
        }
        if (!"http".equals(scheme)) {
            throw invalid("baseUrl", "must use HTTP or HTTPS");
        }
        if (!allowInsecureHttp && !isLoopback(uri.getHost())) {
            throw invalid("baseUrl", "must use HTTPS unless insecure HTTP is explicitly enabled");
        }
    }

    private static boolean isLoopback(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized) || "::1".equals(normalized)
                || "[::1]".equals(normalized) || normalized.startsWith("127.");
    }

    private static String normalizeCommand(String value) {
        String command = Normalizer.normalize(required(value, "command").strip(), Normalizer.Form.NFC);
        if (command.isBlank() || command.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid("command", "must be one nonblank name without control characters");
        }
        return command;
    }

    private static void validateChatFormat(String value) {
        String format = required(value, "chatFormat");
        int conversions = 0;
        for (int index = 0; index < format.length(); index++) {
            if (format.charAt(index) != '%') {
                continue;
            }
            if (index + 1 >= format.length() || format.charAt(index + 1) != 's') {
                throw invalid("chatFormat", "must contain exactly one simple %s conversion");
            }
            conversions++;
            index++;
        }
        if (conversions != 1) {
            throw invalid("chatFormat", "must contain exactly one simple %s conversion");
        }
    }

    private static int occurrences(String value, String target) {
        int count = 0;
        int start = 0;
        while ((start = value.indexOf(target, start)) >= 0) {
            count++;
            start += target.length();
        }
        return count;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "must not be blank");
        }
        return value.strip();
    }

    private static void positive(Duration value, Duration maximum, String field) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(maximum) > 0) {
            throw invalid(field, "must be positive and at most " + maximum);
        }
    }

    private static void positive(int value, int maximum, String field) {
        bounded(value, 1, maximum, field);
    }

    private static void bounded(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw invalid(field, "must be between " + minimum + " and " + maximum);
        }
    }

    private static AiConfigException invalid(String field, String reason) {
        return new AiConfigException(field + " " + reason);
    }
}
