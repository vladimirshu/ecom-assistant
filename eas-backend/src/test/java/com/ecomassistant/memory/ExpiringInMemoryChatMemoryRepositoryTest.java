package com.ecomassistant.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiringInMemoryChatMemoryRepositoryTest {

    private static final Duration TIMEOUT = Duration.ofHours(24);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private ExpiringInMemoryChatMemoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ExpiringInMemoryChatMemoryRepository(clock, TIMEOUT);
    }

    @Test
    void historyIsKeptJustBeforeTimeout() {
        repository.saveAll("a", List.of(new UserMessage("hi")));

        clock.advance(TIMEOUT.minusMinutes(1));

        assertThat(repository.findByConversationId("a")).hasSize(1);
    }

    @Test
    void historyIsGoneAtTimeout() {
        repository.saveAll("a", List.of(new UserMessage("hi")));

        clock.advance(TIMEOUT);

        assertThat(repository.findByConversationId("a")).isEmpty();
        assertThat(repository.findConversationIds()).isEmpty();
    }

    @Test
    void writeRestartsInactivityPeriod() {
        repository.saveAll("a", List.of(new UserMessage("one")));
        clock.advance(Duration.ofHours(23));
        repository.saveAll("a", List.of(new UserMessage("one"), new UserMessage("two")));

        clock.advance(Duration.ofHours(23));

        assertThat(repository.findByConversationId("a")).hasSize(2);
    }

    @Test
    void readsDoNotExtendPeriod() {
        repository.saveAll("a", List.of(new UserMessage("one")));
        clock.advance(Duration.ofHours(23));
        repository.findByConversationId("a");

        clock.advance(Duration.ofHours(1));

        assertThat(repository.findByConversationId("a")).isEmpty();
    }

    @Test
    void emptyConversationAfterExpiryAndAfterDelete() {
        repository.saveAll("a", List.of(new UserMessage("hi")));
        repository.saveAll("b", List.of(new UserMessage("hi")));
        clock.advance(TIMEOUT);
        repository.deleteByConversationId("b");

        assertThat(repository.findByConversationId("a")).isEmpty();
        assertThat(repository.findByConversationId("b")).isEmpty();
    }

    @Test
    void cleanupRemovesExpiredAndKeepsLive() {
        repository.saveAll("old", List.of(new UserMessage("hi")));
        clock.advance(Duration.ofHours(20));
        repository.saveAll("fresh", List.of(new UserMessage("hi")));
        clock.advance(Duration.ofHours(5));

        repository.removeExpired();

        assertThat(repository.findConversationIds()).containsExactly("fresh");
    }

    @Test
    void deleteIsScopedToSingleConversation() {
        repository.saveAll("a", List.of(new UserMessage("one")));
        repository.saveAll("b", List.of(new UserMessage("two")));

        repository.deleteByConversationId("a");

        assertThat(repository.findByConversationId("a")).isEmpty();
        assertThat(repository.findByConversationId("b")).hasSize(1);
    }

    @Test
    void windowKeepsLastTwentyMessages() {
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(20)
                .chatMemoryRepository(repository).build();
        for (int turn = 1; turn <= 12; turn++) {
            memory.add("c", List.<Message>of(new UserMessage("q" + turn), new AssistantMessage("a" + turn)));
        }

        List<String> texts = memory.get("c").stream().map(Message::getText).toList();

        assertThat(texts).hasSize(20);
        assertThat(texts).doesNotContain("q1", "a1", "q2", "a2");
        assertThat(texts).contains("q3", "a12");
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
