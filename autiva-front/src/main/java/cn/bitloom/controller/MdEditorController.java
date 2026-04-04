package cn.bitloom.controller;

import cn.bitloom.util.MarkdownUtil;
import cn.bitloom.window.WindowManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Slf4j
@Component
public class MdEditorController implements WindowManager.StageAware {


    @FXML
    private TextField nameField;
    @FXML
    private TextArea contentArea;
    @FXML
    private WebView previewWebView;
    @FXML
    private Label wordCountLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private SplitPane editorSplitPane;

    @Setter
    private Stage stage;

    @Setter
    private Consumer<MdEditorData> onSaveCallback;

    @Setter
    private Runnable onCloseCallback;

    private WebEngine previewEngine;
    private boolean previewVisible = true;

    @FXML
    public void initialize() {
        this.previewEngine = this.previewWebView.getEngine();
        this.contentArea.textProperty().addListener((obs, oldVal, newVal) -> {
            updatePreview(newVal);
            updateWordCount(newVal);
        });
        updatePreview("");
    }

    public void setTitle(String title) {
        nameField.setPromptText(title);
    }

    public void setName(String name) {
        nameField.setText(name);
    }

    public String getName() {
        return nameField.getText().trim();
    }

    public void setContent(String content) {
        if (content == null) {
            content = "";
        }
        final String finalContent = content;
        if (javafx.application.Platform.isFxApplicationThread()) {
            this.contentArea.setText(finalContent);
            updatePreview(finalContent);
        } else {
            javafx.application.Platform.runLater(() -> {
                this.contentArea.setText(finalContent);
                updatePreview(finalContent);
            });
        }
    }

    public String getContent() {
        return contentArea.getText();
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    @FXML
    private void togglePreview() {
        previewVisible = !previewVisible;
        if (previewVisible) {
            editorSplitPane.setDividerPositions(0.5);
            updatePreview(contentArea.getText());
        } else {
            editorSplitPane.setDividerPositions(1.0);
        }
    }

    @FXML
    private void save() {
        if (onSaveCallback != null) {
            MdEditorData data = new MdEditorData(getName(), getContent());
            onSaveCallback.accept(data);
        }
        if (stage != null) {
            stage.close();
        }
    }

    @FXML
    private void cancel() {
        if (onCloseCallback != null) {
            onCloseCallback.run();
        }
        if (stage != null) {
            stage.close();
        }
    }

    private void updatePreview(String markdown) {
        if (previewEngine == null || !previewVisible) {
            return;
        }
        try {
            String htmlContent = buildHtmlContent(markdown);
            javafx.application.Platform.runLater(() -> previewEngine.loadContent(htmlContent, "text/html"));
        } catch (Exception e) {
            log.error("Failed to update preview", e);
        }
    }

    private String buildHtmlContent(String markdown) {
        String body;
        if (markdown == null || markdown.isEmpty()) {
            body = "<p style='color: #86868b;'>预览区域</p>";
        } else {
            body = MarkdownUtil.renderMarkdown(markdown);
        }
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Roboto, sans-serif;
                        font-size: 14px;
                        line-height: 1.6;
                        padding: 20px;
                        color: #1d1d1f;
                        background-color: #ffffff;
                    }
                    h1, h2, h3, h4, h5, h6 {
                        font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "Segoe UI", Roboto, sans-serif;
                        color: #1d1d1f;
                        margin-top: 1.5em;
                        margin-bottom: 0.5em;
                    }
                    h1 { font-size: 28px%%; font-weight: 700; }
                    h2 { font-size: 22px%%; font-weight: 600; }
                    h3 { font-size: 18px%%; font-weight: 600; }
                    code {
                        font-family: "SF Mono", Menlo, Monaco, monospace;
                        background-color: #f5f5f7;
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-size: 13px;
                    }
                    pre {
                        background-color: #f5f5f7;
                        padding: 16px;
                        border-radius: 8px;
                        overflow-x: auto;
                    }
                    pre code {
                        background-color: transparent;
                        padding: 0;
                    }
                    blockquote {
                        border-left: 4px solid #007aff;
                        margin: 16px 0;
                        padding: 8px 16px;
                        background-color: #f5f5f7;
                        color: #6e6e73;
                    }
                    a {
                        color: #007aff;
                        text-decoration: none;
                    }
                    ul, ol {
                        padding-left: 24px;
                    }
                    li {
                        margin-bottom: 8px;
                    }
                    hr {
                        border: none;
                        border-top: 1px solid #e5e5e7;
                        margin: 24px 0;
                    }
                    table {
                        border-collapse: collapse;
                        width: 100%%;
                        margin: 16px 0;
                    }
                    th, td {
                        border: 1px solid #e5e5e7;
                        padding: 8px 12px;
                        text-align: left;
                    }
                    th {
                        background-color: #f5f5f7;
                    }
                </style>
            </head>
            <body>%s</body>
            </html>
            """.formatted(body);
    }

    private void updateWordCount(String text) {
        int count = text != null ? text.length() : 0;
        wordCountLabel.setText("字数: " + count);
    }

    public record MdEditorData(String name, String content) {}
}
