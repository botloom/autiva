package cn.bitloom.node.message;

import cn.bitloom.util.MarkdownFxRenderer;
import org.springframework.ai.chat.messages.MessageType;
import javafx.application.Platform;
import javafx.beans.property.*;
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
public class AssistantMessageCard extends MessageCard {

    private static final String FONT_FAMILY = "\"SF Pro Text\", -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif";

    // JavaFX 属性（原 ChatMessage 的属性下沉到卡片）
    private final StringProperty content = new SimpleStringProperty("");
    private final ObjectProperty<String> finishReason = new SimpleObjectProperty<>(null);
    private final BooleanProperty streaming = new SimpleBooleanProperty(false);

    // 流式累积器
    private final StringBuilder accumulator = new StringBuilder();
    private boolean isStreamingActive = false;

    @Setter
    private Consumer<String> onContentChanged;

    // 流式期间复用的组件
    private TextFlow streamingContainer = null;
    private Text streamingText = null;

    /** 节流标志：同一 FX 脉冲内多次 chunk 只调度一次 flush，避免逐 chunk 触发 setText+reflow */
    private boolean textUpdateScheduled = false;

    public AssistantMessageCard() {
        this.getStyleClass().add("chat-message");
        this.getStyleClass().add("chat-message--assistant");

        // 预初始化流式容器，确保 card 在被加入 ListView 时有非零 prefHeight。
        // 避免 VirtualFlow 缓存 0 高度导致后续 cell 渲染与滚动范围计算异常。
        initStreamingContainer();

        // contentProperty 监听：流式期间由 flushStreamingText 直接管理，不经过此 listener。
        // 仅用于非流式场景（如历史消息通过 setContent 设置后触发渲染）。
        content.addListener((obs, oldVal, newVal) -> {
            if (!isStreaming() && newVal != null && !newVal.isBlank()) {
                renderMarkdown(newVal);
            }
        });

        // 监听 streamingProperty，流式结束时触发 Markdown 渲染
        streaming.addListener((obs, oldVal, newVal) -> {
            if (!newVal && getContent() != null) {
                renderMarkdown(getContent());
                // MD 渲染后卡片高度可能变化，通知外部重排，避免与下方卡片重叠
                if (onContentChanged != null) {
                    onContentChanged.accept(getContent());
                }
            }
        });
    }

    /**
     * 带初始内容构造（用于历史消息）
     * content.set 触发 contentProperty listener 自动渲染 Markdown。
     */
    public AssistantMessageCard(String initialContent, String finishReason) {
        this();
        if (finishReason != null) {
            this.finishReason.set(finishReason);
        }
        if (initialContent != null) {
            this.content.set(initialContent); // listener 检测到 !isStreaming() → renderMarkdown + updateActionBarVisibility
        }
    }

    // ===== MessageCard 接口实现 =====

    @Override
    public MessageType getMessageType() {
        return MessageType.ASSISTANT;
    }

    @Override
    public String getContent() {
        return content.get();
    }

    // ===== 属性访问器 =====

    public StringProperty contentProperty() {
        return content;
    }

    public void setContent(String value) {
        content.set(value);
    }

    public ObjectProperty<String> finishReasonProperty() {
        return finishReason;
    }

    public String getFinishReason() {
        return finishReason.get();
    }

    public void setFinishReason(String value) {
        finishReason.set(value);
    }

    public BooleanProperty streamingProperty() {
        return streaming;
    }

    public boolean isStreaming() {
        return streaming.get();
    }

    public void setStreaming(boolean value) {
        streaming.set(value);
    }

    // ===== 流式累积方法（原 ChatMessage 的逻辑下沉） =====

    /**
     * 累积流式内容。自动设置 streaming=true，调度节流式 UI 更新。
     * 不直接调用 content.set / setText，而是通过 scheduleFlush 合并同一 FX 脉冲内的多次 chunk。
     */
    public void appendContent(String chunk) {
        if (!isStreamingActive) {
            isStreamingActive = true;
            streaming.set(true);
        }
        accumulator.append(chunk != null ? chunk : "");
        scheduleFlush();
    }

    /**
     * 结束流式输出。取消 pending flush，设置 content，触发 Markdown 渲染。
     * 如果累积内容为空，将 content 设置为 null（供外部判断是否移除）。
     */
    public void complete(String reason) {
        // 取消 pending flush — complete() 将通过 streaming listener 触发最终渲染
        textUpdateScheduled = false;
        isStreamingActive = false;
        // 设置 content 供 isValid 判断使用
        content.set(accumulator.isEmpty() ? null : accumulator.toString());
        finishReason.set(reason);
        streaming.set(false); // 触发 streaming listener → renderMarkdown
    }

    /**
     * 判断消息内容是否有效（非空）。
     */
    public boolean isValid() {
        String c = content.get();
        return c == null || c.isBlank();
    }

    // ===== 渲染逻辑 =====

    /**
     * 调度节流式 UI 更新：同一 FX 脉冲内多次 chunk 只执行一次 flush。
     * appendContent 在 FX 线程调用（由 ViewModel 的 Platform.runLater 保证），
     * 此处再提交一个 runLater，会在当前所有 runLater 之后执行，
     * 从而合并同一脉冲内的多个 chunk 为一次 setText。
     */
    private void scheduleFlush() {
        if (textUpdateScheduled) return;
        textUpdateScheduled = true;
        Platform.runLater(this::flushStreamingText);
    }

    /**
     * 执行节流式 UI 更新：更新 streamingText、请求布局、通知外部重排。
     * 若流式已结束（complete 后残留的 pending flush），直接跳过。
     */
    private void flushStreamingText() {
        if (!isStreamingActive) {
            textUpdateScheduled = false;
            return;
        }
        textUpdateScheduled = false;
        String full = accumulator.toString();
        if (streamingText == null) {
            initStreamingContainer();
        }
        streamingText.setText(full);
        // 请求卡片自身重布局（高度可能变化），确保 VirtualFlow 在下一 pulse 重算 cell 偏移
        this.requestLayout();
        if (onContentChanged != null) {
            onContentChanged.accept(full);
        }
    }

    /**
     * 预初始化流式容器（空内容），确保 card 拥有非零 prefHeight。
     * 在构造函数中调用，避免被加入 ListView 时 VirtualFlow 缓存 0 高度。
     */
    private void initStreamingContainer() {
        streamingContainer = new TextFlow();
        streamingContainer.getStyleClass().add("md-paragraph");
        streamingContainer.getStyleClass().add("chat-message__content");
        streamingContainer.setMaxWidth(Double.MAX_VALUE);

        streamingText = new Text("");
        streamingText.setFont(Font.font(FONT_FAMILY, 15));
        streamingContainer.getChildren().add(streamingText);

        this.getChildren().setAll(streamingContainer);
        this.getStyleClass().add("chat-message--streaming");
    }

    private void renderMarkdown(String content) {
        this.getChildren().clear();
        this.getStyleClass().remove("chat-message--streaming");

        streamingContainer = null;
        streamingText = null;

        if (content == null || content.isBlank()) {
            return;
        }

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
