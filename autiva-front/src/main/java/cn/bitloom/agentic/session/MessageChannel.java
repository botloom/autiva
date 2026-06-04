package cn.bitloom.agentic.session;

import cn.bitloom.agentic.event.EventType;

public enum MessageChannel {
    USER,
    SYSTEM,
    MEMORY,
    JOURNAL,
    EVOLVE;

    public static MessageChannel fromEventType(EventType eventType) {
        if (eventType == null) {
            return USER;
        }
        return switch (eventType) {
            case MESSAGE -> USER;
            case MEMORY_CONSOLIDATE -> MEMORY;
            case JOURNAL -> JOURNAL;
            case EVOLVE -> EVOLVE;
        };
    }

    public boolean shouldPublishToOutBox() {
        return this == USER;
    }
}
