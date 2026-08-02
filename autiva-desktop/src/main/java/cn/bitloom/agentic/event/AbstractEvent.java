package cn.bitloom.agentic.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "eventType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = MessageEvent.class, name = "MESSAGE"),
        @JsonSubTypes.Type(value = UICardEvent.class, name = "UI_CARD"),
        @JsonSubTypes.Type(value = DiffEvent.class, name = "DIFF")
})
public non-sealed abstract class AbstractEvent implements IEvent {
    private String sessionId;

    public abstract EventTypeEnum getEventType();
}
