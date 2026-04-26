package cn.bitloom.node;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class QuestionCard extends VBox {

    private final Map<String, Object> selectedAnswers = new LinkedHashMap<>();

    public QuestionCard(String questionsJson, BiConsumer<String, String> onAnswered) {
        this(questionsJson, UUID.randomUUID().toString(), onAnswered);
    }

    public QuestionCard(String questionsJson, String questionId, BiConsumer<String, String> onAnswered) {
        this.getStyleClass().add("chat-message");
        this.getStyleClass().add("chat-message--tool");
        this.getStyleClass().add("chat-message--question");

        HBox header = new HBox(8);
        header.getStyleClass().add("chat-message__tool-header");

        Label iconLabel = new Label("?");
        iconLabel.getStyleClass().add("chat-message__question-icon");
        Label nameLabel = new Label("AskUserQuestion");
        nameLabel.getStyleClass().add("chat-message__tool-name");
        nameLabel.setStyle("-fx-text-fill: #7c3aed;");
        header.getChildren().addAll(iconLabel, nameLabel);
        this.getChildren().add(header);

        VBox body = new VBox(14);
        body.getStyleClass().add("chat-message__question-body");

        List<JSONObject> questions = parseQuestions(questionsJson);
        List<FlowPane> optionGroups = new ArrayList<>();
        List<Button> submitButtons = new ArrayList<>();

        for (int qIdx = 0; qIdx < questions.size(); qIdx++) {
            JSONObject q = questions.get(qIdx);
            VBox questionItem = new VBox(6);
            questionItem.getStyleClass().add("chat-message__question-item");

            String headerText = q.getString("header");
            if (headerText == null || headerText.isBlank()) {
                headerText = "问题 " + (qIdx + 1);
            }
            Label headerTag = new Label(headerText);
            headerTag.getStyleClass().add("chat-message__question-header-tag");
            questionItem.getChildren().add(headerTag);

            String questionText = q.getString("question");
            Label questionLabel = new Label(questionText);
            questionLabel.getStyleClass().add("chat-message__question-text");
            questionLabel.setWrapText(true);
            questionItem.getChildren().add(questionLabel);

            FlowPane optionsPane = new FlowPane();
            optionsPane.getStyleClass().add("chat-message__question-options");
            optionsPane.setHgap(6);
            optionsPane.setVgap(6);
            optionGroups.add(optionsPane);

            boolean multiSelect = q.getBooleanValue("multiSelect");
            JSONArray options = q.getJSONArray("options");
            if (options != null) {
                for (int oIdx = 0; oIdx < options.size(); oIdx++) {
                    JSONObject opt = options.getJSONObject(oIdx);
                    String optLabel = opt.getString("label");
                    String optDesc = opt.getString("description");

                    Button optionBtn = new Button(optLabel);
                    optionBtn.getStyleClass().add("chat-message__question-option");
                    if (optDesc != null && !optDesc.isBlank()) {
                        optionBtn.setTooltip(new javafx.scene.control.Tooltip(optDesc));
                    }

                    optionBtn.setOnAction(e -> {
                        if (multiSelect) {
                            toggleMultiSelect(optionBtn, questionText, optLabel);
                        } else {
                            optionsPane.getChildren().forEach(node -> {
                                if (node instanceof Button) {
                                    node.getStyleClass().remove("chat-message__question-option--selected");
                                }
                            });
                            optionBtn.getStyleClass().add("chat-message__question-option--selected");
                            selectedAnswers.put(questionText, optLabel);

                            if (questions.size() == 1) {
                                submitAnswers(questions, questionId, onAnswered, optionGroups, submitButtons);
                            } else if (allQuestionsAnswered(questions)) {
                                submitAnswers(questions, questionId, onAnswered, optionGroups, submitButtons);
                            }
                        }
                    });

                    optionsPane.getChildren().add(optionBtn);
                }
            }

            questionItem.getChildren().add(optionsPane);
            body.getChildren().add(questionItem);
        }

        boolean hasMultiSelect = questions.stream().anyMatch(q -> q.getBooleanValue("multiSelect"));
        boolean hasMultipleQuestions = questions.size() > 1;
        if (hasMultiSelect || hasMultipleQuestions) {
            Button submitBtn = new Button("提交");
            submitBtn.getStyleClass().add("chat-message__question-submit");
            submitButtons.add(submitBtn);
            submitBtn.setOnAction(e -> {
                submitAnswers(questions, questionId, onAnswered, optionGroups, submitButtons);
            });
            body.getChildren().add(submitBtn);
        }

        this.getChildren().add(body);
    }

    private boolean allQuestionsAnswered(List<JSONObject> questions) {
        for (JSONObject q : questions) {
            String questionText = q.getString("question");
            if (!selectedAnswers.containsKey(questionText)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void toggleMultiSelect(Button btn, String question, String optLabel) {
        boolean wasSelected = btn.getStyleClass().contains("chat-message__question-option--selected");
        if (wasSelected) {
            btn.getStyleClass().remove("chat-message__question-option--selected");
            Object current = selectedAnswers.get(question);
            if (current instanceof List) {
                ((List<String>) current).remove(optLabel);
                if (((List<String>) current).isEmpty()) {
                    selectedAnswers.remove(question);
                }
            }
        } else {
            btn.getStyleClass().add("chat-message__question-option--selected");
            selectedAnswers.computeIfAbsent(question, k -> new ArrayList<String>());
            if (selectedAnswers.get(question) instanceof List) {
                ((List<String>) selectedAnswers.get(question)).add(optLabel);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void submitAnswers(List<JSONObject> questions, String questionId,
                               BiConsumer<String, String> onAnswered,
                               List<FlowPane> optionGroups, List<Button> submitButtons) {
        Map<String, String> answers = new LinkedHashMap<>();
        for (JSONObject q : questions) {
            String questionText = q.getString("question");
            Object answer = selectedAnswers.get(questionText);
            if (answer instanceof List) {
                answers.put(questionText, String.join(", ", (List<String>) answer));
            } else if (answer != null) {
                answers.put(questionText, answer.toString());
            } else {
                answers.put(questionText, "");
            }
        }

        optionGroups.forEach(pane -> pane.getChildren().forEach(node -> {
            if (node instanceof Button btn) {
                btn.setDisable(true);
                btn.setOpacity(0.7);
            }
        }));
        submitButtons.forEach(btn -> {
            btn.setDisable(true);
            btn.setVisible(false);
        });

        VBox answeredBox = new VBox(4);
        answeredBox.getStyleClass().add("chat-message__question-answered");
        answers.forEach((question, answer) -> {
            Label qLabel = new Label(question);
            qLabel.getStyleClass().add("chat-message__question-answered-label");
            Label aLabel = new Label(answer);
            aLabel.getStyleClass().add("chat-message__question-answered-value");
            aLabel.setWrapText(true);
            answeredBox.getChildren().addAll(qLabel, aLabel);
        });
        this.getChildren().add(answeredBox);

        String answersJson = JSON.toJSONString(answers);
        onAnswered.accept(questionId, answersJson);
    }

    private List<JSONObject> parseQuestions(String questionsJson) {
        try {
            Object parsed = JSON.parse(questionsJson);
            if (parsed instanceof JSONArray arr) {
                return arr.toJavaList(JSONObject.class);
            } else if (parsed instanceof JSONObject obj && obj.containsKey("questions")) {
                return obj.getJSONArray("questions").toJavaList(JSONObject.class);
            }
        } catch (Exception e) {
            // ignore
        }
        return List.of();
    }
}
