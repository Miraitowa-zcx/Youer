package com.mohistmc.youer.ai.history;

import com.mohistmc.youer.ai.model.AiContentPart;
import com.mohistmc.youer.ai.model.AiMessage;
import com.mohistmc.youer.ai.model.AiRole;
import com.mohistmc.youer.ai.model.AiTextContent;
import com.mohistmc.youer.ai.model.AiToolCallContent;
import com.mohistmc.youer.ai.model.AiToolResultContent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class AiConversationStore {

    private static final int DEFAULT_MAX_CONVERSATIONS = 1_024;
    private final ConcurrentHashMap<UUID, Conversation> conversations = new ConcurrentHashMap<>();
    private final AtomicLong versions = new AtomicLong();
    private final Object lifecycleLock = new Object();
    private final Clock clock;
    private final int maxMessages;
    private final int maxCharacters;
    private final Duration idleTimeout;
    private final int maxConversations;

    public AiConversationStore() {
        this(Clock.systemUTC(), 4_096, 1_048_576, Duration.ofMinutes(30),
                DEFAULT_MAX_CONVERSATIONS);
    }

    public AiConversationStore(
            Clock clock,
            int maxMessages,
            int maxCharacters,
            Duration idleTimeout,
            int maxConversations) {
        if (maxMessages < 2 || maxCharacters < 2 || idleTimeout == null
                || idleTimeout.isZero() || idleTimeout.isNegative() || maxConversations < 1) {
            throw new IllegalArgumentException("Conversation limits are invalid");
        }
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.maxMessages = maxMessages;
        this.maxCharacters = maxCharacters;
        this.idleTimeout = idleTimeout;
        this.maxConversations = maxConversations;
    }

    public AiConversationSnapshot snapshot(UUID playerId) {
        synchronized (lifecycleLock) {
            Instant now = clock.instant();
            evictIdle(now);
            Conversation conversation = conversations.computeIfAbsent(
                    playerId, ignored -> new Conversation(versions.incrementAndGet(), now));
            conversation.lastAccess = now;
            evictOverflow(playerId);
            return snapshotOf(conversation);
        }
    }

    public Optional<AiConversationSnapshot> find(UUID playerId) {
        synchronized (lifecycleLock) {
            Instant now = clock.instant();
            evictIdle(now);
            Conversation conversation = conversations.get(playerId);
            if (conversation == null) {
                return Optional.empty();
            }
            conversation.lastAccess = now;
            return Optional.of(snapshotOf(conversation));
        }
    }

    public boolean appendIfVersion(
            UUID playerId,
            long expectedVersion,
            AiMessage userMessage,
            AiMessage assistantMessage,
            int requestedMaxHistory) {
        requireTurn(userMessage, assistantMessage);
        return appendIfVersion(playerId, expectedVersion,
                new AiConversationTurn(List.of(userMessage, assistantMessage)), requestedMaxHistory);
    }

    public boolean appendIfVersion(
            UUID playerId, long expectedVersion, AiConversationTurn turn, int requestedMaxHistory) {
        synchronized (lifecycleLock) {
            Instant now = clock.instant();
            evictIdle(now);
            Conversation conversation = conversations.get(playerId);
            if (conversation == null || conversation.version != expectedVersion) {
                return false;
            }
            int messageLimit = Math.clamp(requestedMaxHistory, 2, maxMessages);
            AiConversationTurn bounded = fitSingleTurn(turn, messageLimit);
            conversation.turns.addLast(bounded);
            conversation.lastAccess = now;
            trim(conversation, messageLimit);
            evictOverflow(playerId);
            return true;
        }
    }

    public void clear(UUID playerId) {
        synchronized (lifecycleLock) {
            if (conversations.remove(playerId) != null) {
                versions.incrementAndGet();
            }
        }
    }

    public void clearAll() {
        synchronized (lifecycleLock) {
            if (!conversations.isEmpty()) {
                versions.incrementAndGet();
                conversations.clear();
            }
        }
    }

    public int size() {
        synchronized (lifecycleLock) {
            evictIdle(clock.instant());
            evictOverflow(null);
            return conversations.size();
        }
    }

    public Map<UUID, AiConversationSnapshot> snapshots() {
        synchronized (lifecycleLock) {
            evictIdle(clock.instant());
            evictOverflow(null);
            Map<UUID, AiConversationSnapshot> result = new LinkedHashMap<>();
            conversations.forEach((playerId, conversation) -> {
                if (!conversation.turns.isEmpty()) {
                    result.put(playerId, snapshotOf(conversation));
                }
            });
            return Map.copyOf(result);
        }
    }

    private void trim(Conversation conversation, int messageLimit) {
        while (conversation.turns.size() > 1
                && (messageCount(conversation.turns) > messageLimit
                || characterCount(conversation.turns) > maxCharacters)) {
            conversation.turns.removeFirst();
        }
    }

    private AiConversationTurn fitSingleTurn(AiConversationTurn turn, int messageLimit) {
        if (turn.messages().size() <= messageLimit && characterCount(turn) <= maxCharacters) {
            return turn;
        }
        String user = turn.messages().getFirst().text();
        String assistant = turn.messages().getLast().text();
        int userBudget = Math.max(1, maxCharacters / 2);
        user = truncateCodePoints(user, userBudget);
        int assistantBudget = Math.max(1, maxCharacters - codePoints(user));
        assistant = truncateCodePoints(assistant, assistantBudget);
        if (user.isEmpty()) user = "…";
        if (assistant.isEmpty()) assistant = "…";
        while (codePoints(user) + codePoints(assistant) > maxCharacters) {
            if (codePoints(user) > 1) user = truncateCodePoints(user, codePoints(user) - 1);
            else assistant = truncateCodePoints(assistant, Math.max(1, codePoints(assistant) - 1));
        }
        return new AiConversationTurn(List.of(
                new AiMessage(AiRole.USER, user),
                new AiMessage(AiRole.ASSISTANT, assistant)));
    }

    private void evictIdle(Instant now) {
        conversations.entrySet().removeIf(entry ->
                !entry.getValue().lastAccess.plus(idleTimeout).isAfter(now));
    }

    private void evictOverflow(UUID protectedPlayer) {
        while (conversations.size() > maxConversations) {
            Map.Entry<UUID, Conversation> oldest = conversations.entrySet().stream()
                    .filter(entry -> protectedPlayer == null || !entry.getKey().equals(protectedPlayer))
                    .min(Comparator
                            .comparing((Map.Entry<UUID, Conversation> entry) -> entry.getValue().lastAccess)
                            .thenComparingLong(entry -> entry.getValue().version))
                    .orElse(null);
            if (oldest == null) {
                return;
            }
            conversations.remove(oldest.getKey(), oldest.getValue());
        }
    }

    private static AiConversationSnapshot snapshotOf(Conversation conversation) {
        List<AiMessage> messages = conversation.turns.stream()
                .flatMap(turn -> turn.messages().stream()).toList();
        return new AiConversationSnapshot(conversation.version, messages);
    }

    private static int messageCount(Iterable<AiConversationTurn> turns) {
        int count = 0;
        for (AiConversationTurn turn : turns) count += turn.messages().size();
        return count;
    }

    private static int characterCount(Iterable<AiConversationTurn> turns) {
        int count = 0;
        for (AiConversationTurn turn : turns) count += characterCount(turn);
        return count;
    }

    private static int characterCount(AiConversationTurn turn) {
        int count = 0;
        for (AiMessage message : turn.messages()) {
            for (AiContentPart part : message.content()) {
                if (part instanceof AiTextContent(String text1)) {
                    count += codePoints(text1);
                } else if (part instanceof AiToolCallContent call) {
                    count += codePoints(call.id()) + codePoints(call.name())
                            + codePoints(call.arguments().toString());
                } else if (part instanceof AiToolResultContent result) {
                    count += codePoints(result.callId()) + codePoints(result.name())
                            + codePoints(result.content());
                }
            }
        }
        return count;
    }

    private static String truncateCodePoints(String value, int maximum) {
        int count = codePoints(value);
        return count <= maximum ? value : value.substring(0, value.offsetByCodePoints(0, maximum));
    }

    private static int codePoints(String value) {
        return value.codePointCount(0, value.length());
    }

    private static void requireTurn(AiMessage userMessage, AiMessage assistantMessage) {
        if (userMessage.role() != AiRole.USER || assistantMessage.role() != AiRole.ASSISTANT) {
            throw new IllegalArgumentException("Conversation history accepts only complete USER/ASSISTANT turns");
        }
    }

    private static final class Conversation {
        private final long version;
        private final ArrayDeque<AiConversationTurn> turns = new ArrayDeque<>();
        private Instant lastAccess;

        private Conversation(long version, Instant lastAccess) {
            this.version = version;
            this.lastAccess = lastAccess;
        }
    }
}
