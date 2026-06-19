package cn.bitloom.agentic.event;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
public abstract sealed class AbstractEvent implements IEvent permits MessageEvent, MemoryEvent {
    private String sessionId;
}
