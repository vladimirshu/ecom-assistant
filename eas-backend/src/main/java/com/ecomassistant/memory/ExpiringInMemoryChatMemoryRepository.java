package com.ecomassistant.memory;

import com.ecomassistant.config.EcomMemoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process {@link ChatMemoryRepository} that drops a conversation once no turn has been
 * stored for it during the inactivity timeout. Every {@link #saveAll} restarts the period;
 * reads do not. Expired entries are treated as absent on read and swept periodically.
 */
public class ExpiringInMemoryChatMemoryRepository implements ChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(ExpiringInMemoryChatMemoryRepository.class);

    private record StoredConversation(Instant lastActivity, List<Message> messages) {
    }

    private final Map<String, StoredConversation> conversations = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration inactivityTimeout;

    public ExpiringInMemoryChatMemoryRepository(Clock clock, EcomMemoryProperties properties) {
        this(clock, properties.inactivityTimeout());
    }

    public ExpiringInMemoryChatMemoryRepository(Clock clock, Duration inactivityTimeout) {
        this.clock = clock;
        this.inactivityTimeout = inactivityTimeout;
    }

    @Override
    public List<String> findConversationIds() {
        return conversations.keySet().stream()
                .filter(id -> !findAndExpire(id).isEmpty())
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return findAndExpire(conversationId);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Instant now = clock.instant();
        conversations.compute(conversationId,
                (id, existing) -> new StoredConversation(now, List.copyOf(messages)));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        conversations.remove(conversationId);
    }

    @Scheduled(fixedDelayString = "${ecom.memory.cleanup-interval:1h}")
    public void removeExpired() {
        Instant now = clock.instant();
        int before = conversations.size();
        conversations.entrySet().removeIf(e -> isExpired(e.getValue(), now));
        int removed = before - conversations.size();
        if (removed > 0) {
            log.debug("Removed {} expired conversations", removed);
        }
    }

    private List<Message> findAndExpire(String conversationId) {
        Instant now = clock.instant();
        StoredConversation stored = conversations.get(conversationId);
        if (stored == null) {
            return List.of();
        }
        if (isExpired(stored, now)) {
            conversations.remove(conversationId, stored);
            return List.of();
        }
        return stored.messages();
    }

    private boolean isExpired(StoredConversation stored, Instant now) {
        return !stored.lastActivity().plus(inactivityTimeout).isAfter(now);
    }
}
