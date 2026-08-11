package cn.bitloom.node.terminal;

import com.techsenger.jeditermfx.ui.JediTermFxWidget;
import com.techsenger.jeditermfx.ui.settings.SettingsProvider;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 JediTermFX 的终端视图组件
 * 封装 JediTermFxWidget，提供完整的终端交互能力（ANSI 颜色、光标控制、全屏程序支持）
 */
@Slf4j
public class JediTerminalView extends BorderPane {

    private static final int DEFAULT_COLUMNS = 80;
    private static final int DEFAULT_ROWS = 24;

    private final SettingsProvider settingsProvider;

    private JediTermFxWidget widget;
    private PtySession session;

    public JediTerminalView() {
        this(new LightSettingsProvider());
    }

    public JediTerminalView(SettingsProvider settingsProvider) {
        this.settingsProvider = settingsProvider;
        getStyleClass().add("jedi-terminal-view");
        getStylesheets().add(getClass().getResource("/cn/bitloom/style/terminal.css").toExternalForm());
    }

    /**
     * 启动终端会话
     */
    public void startSession(PtySession session) {
        this.session = session;
        PtySessionTtyConnector connector = new PtySessionTtyConnector(session);
        this.widget = new JediTermFxWidget(DEFAULT_COLUMNS, DEFAULT_ROWS, settingsProvider);
        this.widget.setTtyConnector(connector);

        // 用 StackPane 包装 widget，添加 padding 让内容不覆盖圆角区域
        StackPane wrapper = new StackPane();
        wrapper.getStyleClass().add("terminal-wrapper");
        wrapper.setPadding(new Insets(8, 8, 8, 8));  // 上 右 下 左
        wrapper.getChildren().add(widget.getPane());

        setCenter(wrapper);
        this.widget.start();
        log.info("JediTermFX 终端会话已启动: {}", session.getSessionId());
    }

    /**
     * 关闭终端会话
     */
    public void closeSession() {
        if (widget != null) {
            try {
                widget.close();
            } catch (Exception e) {
                log.warn("关闭 JediTermFxWidget 异常", e);
            }
            widget = null;
        }
        if (session != null) {
            session.close();
            session = null;
        }
        log.info("JediTermFX 终端会话已关闭");
    }

    /**
     * 检查终端是否正在运行
     */
    public boolean isRunning() {
        return session != null && session.isAlive();
    }

    @Override
    public void requestFocus() {
        super.requestFocus();
        if (widget != null) {
            widget.getPreferredFocusableNode().requestFocus();
        }
    }

    /**
     * 获取终端当前选中的文本（可能为 null 或空字符串）。
     * 通过 selectedTextProperty().get() 获取，兼容 JediTermFX 1.1.0。
     */
    public String getSelectedText() {
        if (widget != null) {
            ReadOnlyStringProperty prop = widget.getTerminalPanel().selectedTextProperty();
            return prop != null ? prop.get() : null;
        }
        return null;
    }

    /**
     * 终端选中文本属性（ReadOnlyStringProperty），可在 widget 就绪后监听选择变化。
     * 仅在会话启动后可用，启动前返回 null。
     */
    public ReadOnlyStringProperty selectedTextProperty() {
        if (widget != null) {
            return widget.getTerminalPanel().selectedTextProperty();
        }
        return null;
    }
}
