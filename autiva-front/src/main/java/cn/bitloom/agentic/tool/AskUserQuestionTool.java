package cn.bitloom.agentic.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AskUserQuestionTool implements ITool {

    private static final int DEFAULT_TIMEOUT = 300;
    private static final int MAX_QUESTIONS = 4;
    private static final int MAX_OPTIONS = 4;

    private final ApplicationEventPublisher eventPublisher;

    private final ConcurrentHashMap<String, CompletableFuture<UserResponse>> pendingQuestions = new ConcurrentHashMap<>();

    @Tool(name = "ask_user", description = "向用户提问并等待回答。支持单选、多选、自定义输入。用于澄清需求、获取决策、收集偏好。")
    public ToolResult askUser(
            @ToolParam(description = "问题列表（最多4个），JSON数组格式") String questions) {
        
        String questionId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[ToolCall] ask_user - 向用户提问: questionId={}", questionId);
        
        if (StringUtils.isBlank(questions)) {
            return ToolResult.failure("错误：问题不能为空");
        }

        try {
            List<Question> questionList = parseQuestions(questions);
            
            if (questionList.isEmpty()) {
                return ToolResult.failure("错误：未能解析出有效问题");
            }

            if (questionList.size() > MAX_QUESTIONS) {
                return ToolResult.failure("错误：最多支持 " + MAX_QUESTIONS + " 个问题");
            }

            for (Question q : questionList) {
                if (q.options != null && q.options.size() > MAX_OPTIONS) {
                    return ToolResult.failure("错误：每个问题最多 " + MAX_OPTIONS + " 个选项");
                }
            }

            CompletableFuture<UserResponse> future = new CompletableFuture<>();
            pendingQuestions.put(questionId, future);

            AskUserEvent event = new AskUserEvent(questionId, questionList);
            eventPublisher.publishEvent(event);

            log.info("[ToolCall] ask_user - 等待用户回答: questionId={}", questionId);

            UserResponse response = future.get(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
            pendingQuestions.remove(questionId);

            log.info("[ToolCall] ask_user - 用户已回答: questionId={}", questionId);
            return ToolResult.success("用户已回答", formatResponse(response));

        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("[ToolCall] ask_user - 等待超时: questionId={}", questionId);
            pendingQuestions.remove(questionId);
            return ToolResult.failure("等待用户回答超时（" + DEFAULT_TIMEOUT + "秒）");
        } catch (Exception e) {
            log.error("[ToolCall] ask_user - 提问失败: questionId={}", questionId, e);
            pendingQuestions.remove(questionId);
            return ToolResult.failure("提问失败: " + e.getMessage());
        }
    }

    public void submitResponse(String questionId, UserResponse response) {
        CompletableFuture<UserResponse> future = pendingQuestions.get(questionId);
        if (future != null) {
            future.complete(response);
            log.info("[ToolCall] ask_user - 收到用户回答: questionId={}", questionId);
        }
    }

    private List<Question> parseQuestions(String json) {
        List<Question> questions = new ArrayList<>();
        
        try {
            json = json.trim();
            if (json.startsWith("[")) {
                json = json.substring(1);
            }
            if (json.endsWith("]")) {
                json = json.substring(0, json.length() - 1);
            }

            String[] items = json.split("\\},\\s*\\{");
            for (String item : items) {
                item = item.replaceAll("^\\{|\\}$", "").trim();
                if (item.isEmpty()) continue;

                Question q = new Question();
                
                String header = extractValue(item, "header");
                String question = extractValue(item, "question");
                String multiSelect = extractValue(item, "multiSelect");
                String optionsStr = extractValue(item, "options");

                q.header = header != null ? header : "问题";
                q.question = question != null ? question : item;
                q.multiSelect = "true".equalsIgnoreCase(multiSelect);

                if (optionsStr != null) {
                    q.options = parseOptions(optionsStr);
                }

                questions.add(q);
            }
        } catch (Exception e) {
            log.warn("[ToolCall] ask_user - 解析问题失败: {}", e.getMessage());
            questions.add(new Question("问题", json, null, false));
        }

        return questions;
    }

    private String extractValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"";
        int start = json.indexOf(pattern);
        if (start == -1) {
            pattern = "\"" + key + "\"\\s*:\\s*";
            start = json.indexOf(pattern);
            if (start == -1) return null;
            start += pattern.length();
            
            if (json.charAt(start) == '[') {
                int end = json.indexOf(']', start);
                if (end > start) {
                    return json.substring(start, end + 1);
                }
            } else if (json.charAt(start) == 't' || json.charAt(start) == 'f') {
                return json.substring(start, start + (json.startsWith("true", start) ? 4 : 5));
            }
            return null;
        }
        
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end > start) {
            return json.substring(start, end);
        }
        return null;
    }

    private List<Option> parseOptions(String optionsStr) {
        List<Option> options = new ArrayList<>();
        
        optionsStr = optionsStr.trim();
        if (optionsStr.startsWith("[")) {
            optionsStr = optionsStr.substring(1);
        }
        if (optionsStr.endsWith("]")) {
            optionsStr = optionsStr.substring(0, optionsStr.length() - 1);
        }

        String[] items = optionsStr.split("\\},\\s*\\{");
        for (String item : items) {
            item = item.replaceAll("^\\{|\\}$", "").trim();
            if (item.isEmpty()) continue;

            String label = extractValue(item, "label");
            String description = extractValue(item, "description");
            
            if (label != null) {
                options.add(new Option(label, description != null ? description : ""));
            }
        }

        return options;
    }

    private String formatResponse(UserResponse response) {
        StringBuilder output = new StringBuilder();
        output.append("用户回答:\n\n");
        
        if (response.answers != null && !response.answers.isEmpty()) {
            for (int i = 0; i < response.answers.size(); i++) {
                Answer answer = response.answers.get(i);
                output.append(String.format("%d. %s\n", i + 1, answer.question));
                output.append(String.format("   回答: %s\n", answer.answer));
                if (answer.selectedOptions != null && !answer.selectedOptions.isEmpty()) {
                    output.append(String.format("   选中选项: %s\n", String.join(", ", answer.selectedOptions)));
                }
                output.append("\n");
            }
        }

        if (response.otherInput != null && !response.otherInput.isEmpty()) {
            output.append("用户自定义输入:\n").append(response.otherInput);
        }

        return output.toString();
    }

    public static class Question {
        public String header;
        public String question;
        public List<Option> options;
        public boolean multiSelect;

        public Question() {}

        public Question(String header, String question, List<Option> options, boolean multiSelect) {
            this.header = header;
            this.question = question;
            this.options = options;
            this.multiSelect = multiSelect;
        }
    }

    public static class Option {
        public String label;
        public String description;

        public Option(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    public static class UserResponse {
        public List<Answer> answers;
        public String otherInput;

        public UserResponse() {
            this.answers = new ArrayList<>();
        }
    }

    public static class Answer {
        public String question;
        public String answer;
        public List<String> selectedOptions;

        public Answer(String question, String answer, List<String> selectedOptions) {
            this.question = question;
            this.answer = answer;
            this.selectedOptions = selectedOptions;
        }
    }

    public static class AskUserEvent {
        public final String questionId;
        public final List<Question> questions;

        public AskUserEvent(String questionId, List<Question> questions) {
            this.questionId = questionId;
            this.questions = questions;
        }
    }
}
