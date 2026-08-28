package com.mohistmc.youer.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class AiResponseFormatter {

    private static final int MAX_LINES = 8;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public Component format(String configuredFormat, String providerText, int maxResponseChars) {
        int conversion = configuredFormat.indexOf("%s");
        if (conversion < 0 || conversion != configuredFormat.lastIndexOf("%s")) {
            throw new IllegalArgumentException("AI chat format must contain exactly one %s conversion");
        }
        String prefix = configuredFormat.substring(0, conversion);
        String suffix = configuredFormat.substring(conversion + 2);
        String literal = boundLines(removeControls(providerText == null ? "" : providerText));
        literal = truncateCodePoints(literal, maxResponseChars);
        return Component.empty()
                .append(LEGACY.deserialize(prefix))
                .append(Component.text(literal))
                .append(LEGACY.deserialize(suffix));
    }

    private static String removeControls(String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            if (codePoint == '\n' || !Character.isISOControl(codePoint)) {
                result.appendCodePoint(codePoint);
            }
        });
        return result.toString();
    }

    private static String boundLines(String value) {
        String[] lines = value.split("\n", -1);
        int count = Math.min(lines.length, MAX_LINES);
        List<String> bounded = new ArrayList<>(count);
        bounded.addAll(Arrays.asList(lines).subList(0, count));
        return String.join("\n", bounded);
    }

    private static String truncateCodePoints(String value, int maximum) {
        if (maximum < 1) {
            throw new IllegalArgumentException("Maximum response characters must be positive");
        }
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= maximum) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maximum));
    }
}
