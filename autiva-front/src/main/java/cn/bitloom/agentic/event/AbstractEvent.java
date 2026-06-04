package cn.bitloom.agentic.event;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@Setter
@SuperBuilder
public abstract class AbstractEvent {
    public String sessionId;
    @Builder.Default
    public LocalDateTime timestamp=LocalDateTime.now();
}
