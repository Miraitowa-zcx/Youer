package com.mohistmc.youer.ai.config;

import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AiSecretResolver {

    private static final Pattern ENVIRONMENT = Pattern.compile("^\\$\\{ENV:([A-Za-z_][A-Za-z0-9_]*)}$");
    private final Function<String, String> environment;

    public AiSecretResolver(Function<String, String> environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    public String resolve(String value) {
        String candidate = value == null ? "" : value;
        Matcher matcher = ENVIRONMENT.matcher(candidate);
        if (!matcher.matches()) {
            return candidate;
        }
        String name = matcher.group(1);
        String resolved = environment.apply(name);
        if (resolved == null) {
            throw new AiConfigException("apiKey environment variable is missing: " + name);
        }
        return resolved;
    }
}
