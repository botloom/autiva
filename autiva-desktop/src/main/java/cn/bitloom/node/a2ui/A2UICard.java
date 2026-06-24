package cn.bitloom.node.a2ui;

import cn.bitloom.agentic.a2ui.A2UIMessage;
import javafx.application.Platform;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A2UI Surface 的 JavaFX 容器。
 * <p>
 * 继承 VBox,作为聊天消息插入到聊天流中。
 * 内部持有 A2UISurface 管理组件树和数据模型。
 */
@Slf4j
public class A2UICard extends VBox {

    private final A2UISurface surface;

    public A2UICard(String surfaceId) {
        this.surface = new A2UISurface(surfaceId);
        getStyleClass().add("a2ui-card");
        setSpacing(8);
    }

    /**
     * 设置用户交互回调。
     *
     * @param callback (actionName, context) → 通过 ToolUIBridge 回流到 Agent
     */
    public void setOnUserAction(BiConsumer<String, Map<String, Object>> callback) {
        surface.setOnUserAction(callback);
    }

    /**
     * 处理 A2UI 消息,更新 UI。
     * <p>
     * 必须在 JavaFX Application Thread 调用。
     */
    public void handleMessage(A2UIMessage message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> handleMessageInternal(message));
            return;
        }
        handleMessageInternal(message);
    }

    private void handleMessageInternal(A2UIMessage message) {
        try {
            surface.handleMessage(message);

            // 更新 UI:清空旧内容,渲染新内容
            if (message instanceof A2UIMessage.UpdateComponents) {
                getChildren().clear();
                if (surface.getRootNode() != null) {
                    getChildren().add(surface.getRootNode());
                }
            } else if (message instanceof A2UIMessage.UpdateDataModel) {
                // 数据模型更新后重新渲染
                getChildren().clear();
                if (surface.getRootNode() != null) {
                    getChildren().add(surface.getRootNode());
                }
            } else if (message instanceof A2UIMessage.DeleteSurface) {
                getChildren().clear();
            }
        } catch (Exception e) {
            log.error("Failed to handle A2UI message", e);
        }
    }

    public String getSurfaceId() {
        return surface.getSurfaceId();
    }

    public A2UISurface getSurface() {
        return surface;
    }
}
