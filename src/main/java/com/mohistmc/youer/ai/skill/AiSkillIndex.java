package com.mohistmc.youer.ai.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AiSkillIndex {

    public static final int DEFAULT_MAX_SKILLS = 128;
    public static final int DEFAULT_MAX_CHARACTERS = 12_000;
    private static final Pattern MESSAGE_TOKEN = Pattern.compile("[\\p{L}\\p{N}_:.-]+");
    private static final String HEADER = """
            Available permission-filtered Skills follow. Call load_skill with an exact id before use; actions still require visible tools.
            """;

    private final int maxSkills;
    private final int maxCharacters;

    public AiSkillIndex() {
        this(DEFAULT_MAX_SKILLS, DEFAULT_MAX_CHARACTERS);
    }

    public AiSkillIndex(int maxSkills, int maxCharacters) {
        if (maxSkills < 1 || maxCharacters < HEADER.length() + 32) {
            throw new IllegalArgumentException("Skill index limits are too small");
        }
        this.maxSkills = maxSkills;
        this.maxCharacters = maxCharacters;
    }

    public String format(AiSkillSnapshot snapshot) {
        return format(snapshot, "");
    }

    public String format(AiSkillSnapshot snapshot, String userMessage) {
        return format(catalog(snapshot), userMessage);
    }

    Catalog catalog(AiSkillSnapshot snapshot) {
        List<Entry> entries = snapshot.skills().stream()
                .map(skill -> new Entry(skill, line(skill)))
                .toList();
        return new Catalog(entries);
    }

    String format(Catalog catalog, String userMessage) {
        List<Entry> entries = new ArrayList<>(catalog.entries());
        Set<String> tokens = messageTokens(userMessage);
        entries.sort(Comparator
                .comparingInt((Entry entry) -> rank(entry.skill(), tokens))
                .thenComparing(entry -> entry.skill().id()));
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(HEADER);
        int included = 0;
        for (Entry entry : entries) {
            if (included >= maxSkills) {
                break;
            }
            String line = entry.line();
            if (result.length() + line.length() > maxCharacters) {
                break;
            }
            result.append(line);
            included++;
        }
        int omitted = entries.size() - included;
        if (omitted > 0) {
            String note = "- ... %d additional Skills omitted by index bounds%n".formatted(omitted);
            if (result.length() + note.length() <= maxCharacters) {
                result.append(note);
            }
        }
        return result.toString();
    }

    private static String line(AiSkill skill) {
        return "- %s | %s | execution=%s | risk=%s%n".formatted(
                skill.id(), compact(skill.description()),
                skill.execution().name().toLowerCase(Locale.ROOT), skill.risk().name());
    }

    private static int rank(AiSkill skill, Set<String> messageTokens) {
        boolean exactCommandMatch = skill.commands().stream()
                .map(AiSkillIndex::commandToken)
                .anyMatch(messageTokens::contains);
        if (exactCommandMatch) return 0;
        return skill.id().startsWith("workflow.") ? 1 : 2;
    }

    private static Set<String> messageTokens(String message) {
        Set<String> tokens = new HashSet<>();
        Matcher matcher = MESSAGE_TOKEN.matcher(message == null ? "" : message.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private static String commandToken(String command) {
        String normalized = command == null ? "" : command.strip().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        int space = normalized.indexOf(' ');
        return space < 0 ? normalized : normalized.substring(0, space);
    }

    private static String compact(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    record Catalog(List<Entry> entries) {
        Catalog {
            entries = List.copyOf(entries);
        }
    }

    private record Entry(AiSkill skill, String line) {
    }
}
