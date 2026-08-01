package cn.bitloom.agentic.event;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@Getter
@Setter
@SuperBuilder
@Jacksonized
@NoArgsConstructor
public final class UICardEvent extends AbstractEvent {

    public enum Type { TASK_CARD, QUESTION_CARD }
    public enum Status { CREATED, COMPLETED, FAILED, ANSWERED }

    @Builder.Default
    private EventTypeEnum eventType = EventTypeEnum.UI_CARD;

    private Type type;
    private String cardId;
    private String cardJson;
    private Status status;
    private String result;

    @Builder.Default
    private boolean persist = false;

    @Override
    public EventTypeEnum getEventType() { return eventType; }

    public static UICardEvent taskCreated(String sessionId, String taskId, String taskJson) {
        return UICardEvent.builder()
                .sessionId(sessionId).type(Type.TASK_CARD).cardId(taskId)
                .cardJson(taskJson).status(Status.CREATED).persist(false)
                .build();
    }

    public static UICardEvent taskCompleted(String sessionId, String taskId, String taskJson, String result) {
        return UICardEvent.builder()
                .sessionId(sessionId).type(Type.TASK_CARD).cardId(taskId)
                .cardJson(taskJson).status(Status.COMPLETED).result(result).persist(true)
                .build();
    }

    public static UICardEvent taskFailed(String sessionId, String taskId, String taskJson, String error) {
        return UICardEvent.builder()
                .sessionId(sessionId).type(Type.TASK_CARD).cardId(taskId)
                .cardJson(taskJson).status(Status.FAILED).result(error).persist(true)
                .build();
    }

    public static UICardEvent questionAsked(String sessionId, String questionId, String questionsJson) {
        return UICardEvent.builder()
                .sessionId(sessionId).type(Type.QUESTION_CARD).cardId(questionId)
                .cardJson(questionsJson).status(Status.CREATED).persist(false)
                .build();
    }

    public static UICardEvent questionAnswered(String sessionId, String questionId, String questionsJson, String answersJson) {
        return UICardEvent.builder()
                .sessionId(sessionId).type(Type.QUESTION_CARD).cardId(questionId)
                .cardJson(questionsJson).status(Status.ANSWERED).result(answersJson).persist(true)
                .build();
    }

    public boolean isTaskCard() { return type == Type.TASK_CARD; }
    public boolean isQuestionCard() { return type == Type.QUESTION_CARD; }
}
