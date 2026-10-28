package com.ecomassistant.memory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Serializes requests, including resets, for each conversation without retaining idle locks. */
public final class ConversationLocks {
    private final ConcurrentHashMap<String, Entry> locks = new ConcurrentHashMap<>();

    public <T> T withLock(String conversationId, Supplier<T> action) {
        Entry entry = locks.compute(conversationId, (key, current) -> {
            Entry result = current == null ? new Entry() : current;
            result.users++;
            return result;
        });
        entry.lock.lock();
        try {
            return action.get();
        } finally {
            entry.lock.unlock();
            locks.compute(conversationId, (key, current) -> --current.users == 0 ? null : current);
        }
    }

    private static final class Entry {
        private final ReentrantLock lock = new ReentrantLock(true);
        // Accessed only within the map's per-key compute operation.
        private int users;
    }
}
