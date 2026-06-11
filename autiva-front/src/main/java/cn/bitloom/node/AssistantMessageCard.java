package cn.bitloom.node;

import cn.bitloom.util.MarkdownFxRenderer;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Getter
@Slf4j
public class AssistantMessageCard extends VBox {

    private static final String FONT_FAMILY = "\"SF Pro Text\", -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif";

    private final ChatMessage chatMessage;
    @Setter
    private Consumer<String> onContentChanged;

    public AssistantMessageCard(ChatMessage chatMessage) {
        this.chatMessage = chatMessage;
        this.getStyleClass().add("chat-message");
        this.getStyleClass().add("chat-message--assistant");

        renderContent(chatMessage.getContent(), chatMessage.isStreaming());

        chatMessage.contentProperty().addListener((obs, oldVal, newVal) -> {
            renderContent(newVal, chatMessage.isStreaming());
            if (onContentChanged != null && newVal != null) {
                onContentChanged.accept(newVal);
            }
        });

        chatMessage.streamingProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                renderContent(chatMessage.getContent(), false);
                this.getStyleClass().remove("chat-message--streaming");
            }
        });

        if (chatMessage.isStreaming()) {
            this.getStyleClass().add("chat-message--streaming");
        }
    }

    private void renderContent(String content, boolean streaming) {
        this.getChildren().clear();
        if (content == null || content.isBlank()) {
            return;
        }

        if (streaming) {
            renderLightweight(content);
        } else {
            renderMarkdown(content);
        }
    }

    private void renderLightweight(String content) {
        TextFlow textFlow = new TextFlow();
        textFlow.getStyleClass().add("md-paragraph");
        textFlow.getStyleClass().add("chat-message__content");
        textFlow.setMaxWidth(Double.MAX_VALUE);
        Text text = new Text(content);
        text.setFont(Font.font(FONT_FAMILY, 15));
        textFlow.getChildren().add(text);
        this.getChildren().add(textFlow);
    }

    private void renderMarkdown(String content) {
        try {
            VBox rendered = MarkdownFxRenderer.render(content);
            List<Node> childrenCopy = new ArrayList<>(rendered.getChildren());
            for (Node child : childrenCopy) {
                if (child instanceof TextFlow tf) {
                    tf.setMaxWidth(Double.MAX_VALUE);
                    tf.getStyleClass().add("chat-message__content");
                } else if (child instanceof Region region) {
                    region.setMaxWidth(Double.MAX_VALUE);
                }
                this.getChildren().add(child);
            }
        } catch (Exception e) {
            log.error("Markdown渲染失败，使用TextFlow回退", e);
            TextFlow textFlow = new TextFlow();
            textFlow.getStyleClass().add("chat-message__content");
            Text text = new Text(content);
            text.setFont(Font.font(FONT_FAMILY, 15));
            textFlow.getChildren().add(text);
            this.getChildren().add(textFlow);
        }
    }

}
