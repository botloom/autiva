package cn.bitloom.agentic.util;

import cn.bitloom.agentic.tool.interaction.AskUserQuestionTool.Question;
import cn.bitloom.agentic.tool.interaction.AskUserQuestionTool.QuestionHandler;
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RequiredArgsConstructor
public class GuiQuestionHandler implements QuestionHandler {

    private final ToolUIBridge toolUIBridge;
    private final long timeoutMinutes;

    public GuiQuestionHandler(ToolUIBridge toolUIBridge) {
        this(toolUIBridge, 10);
    }

    @Override
    public Map<String, String> handle(List<Question> questions) {
        try {
            String questionsJson = JsonUtils.toJson(questions);
            CompletableFuture<String> answerFuture = new CompletableFuture<>();

            this.toolUIBridge.showQuestions(questionsJson, answerFuture);

            String answersJson = answerFuture.get(this.timeoutMinutes, TimeUnit.MINUTES);

            return parseAnswers(questions, answersJson);
        } catch (TimeoutException e) {
            log.error("Question answer timeout after {} minutes", this.timeoutMinutes);
            Map<String, String> defaultAnswers = new HashMap<>();
            for (Question q : questions) {
                defaultAnswers.put(q.question(), "");
            }
            return defaultAnswers;
        } catch (Exception e) {
            log.error("Error waiting for question answers", e);
            Map<String, String> defaultAnswers = new HashMap<>();
            for (Question q : questions) {
                defaultAnswers.put(q.question(), "");
            }
            return defaultAnswers;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseAnswers(List<Question> questions, String answersJson) {
        Map<String, String> answers = new HashMap<>();
        try {
            JsonNode jsonObj = JsonUtils.parse(answersJson);
            for (Question q : questions) {
                String answer = jsonObj.has(q.question()) ? jsonObj.get(q.question()).asText() : null;
                answers.put(q.question(), answer != null ? answer : "");
            }
        } catch (Exception e) {
            log.error("Error parsing answers JSON: {}", answersJson, e);
            for (Question q : questions) {
                answers.put(q.question(), "");
            }
        }
        return answers;
    }
}
