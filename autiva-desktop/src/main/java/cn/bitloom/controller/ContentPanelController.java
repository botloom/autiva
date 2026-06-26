package cn.bitloom.controller;

import cn.bitloom.agentic.diff.DiffService;
import cn.bitloom.agentic.diff.FileDiff;
import cn.bitloom.node.terminal.JediTerminalView;
import cn.bitloom.node.terminal.PtySession;
import cn.bitloom.node.terminal.PtyTerminalService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;

/**
 * 内容面板控制器
 * 管理终端、文件内容、diff 视图的显示，与主区域并列的独立区域
 */
@Slf4j
@Component
public class ContentPanelController implements Initializable {

    @FXML
    @Getter
    private VBox contentPanel;
    @FXML
    private Label contentTitle;
    @FXML
    private Button closeContentButton;
    @FXML
    private ScrollPane contentScrollPane;
    @FXML
    private VBox contentContainer;

    @Getter
    @Setter
    private IndexController indexController;

    private final PtyTerminalService ptyTerminalService;
    private final DiffService diffService;

    private JediTerminalView terminalView;
    private PtySession terminalSession;
    private Path lastTerminalWorkingDir;

    public ContentPanelController(PtyTerminalService ptyTerminalService, DiffService diffService) {
        this.ptyTerminalService = ptyTerminalService;
        this.diffService = diffService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 初始化完成
    }

    /**
     * 显示内容面板
     */
    public void show() {
        contentPanel.setVisible(true);
        contentPanel.setManaged(true);
    }

    /**
     * 隐藏内容面板
     */
    public void hide() {
        contentPanel.setVisible(false);
        contentPanel.setManaged(false);
    }

    /**
     * 检查面板是否可见
     */
    public boolean isVisible() {
        return contentPanel.isVisible();
    }

    /**
     * 显示文件内容
     */
    public void showFileContent(Path filePath) {
        try {
            String content = Files.readString(filePath);
            Platform.runLater(() -> {
                // 关闭终端（如果有）
                closeTerminalInternal();
                // 恢复 ScrollPane 可见性
                restoreScrollPane();
                contentTitle.setText(filePath.getFileName().toString());
                contentContainer.getChildren().clear();
                Text text = new Text(content);
                text.getStyleClass().add("content-panel__file-content");
                contentContainer.getChildren().add(text);
                show();
            });
        } catch (IOException e) {
            log.warn("读取文件失败: {}", filePath, e);
            showErrorContent("无法读取文件: " + filePath.getFileName(), null);
        } catch (Exception e) {
            log.error("显示文件内容失败: {}", filePath, e);
            showErrorContent("显示文件失败: " + e.getMessage(), null);
        }
    }

    /**
     * 显示 diff 视图（带审核按钮）
     */
    public void showDiffView(FileDiff diff) {
        Platform.runLater(() -> {
            // 关闭终端（如果有）
            closeTerminalInternal();
            // 恢复 ScrollPane 可见性
            restoreScrollPane();
            contentTitle.setText("Diff: " + diff.filePath());
            contentContainer.getChildren().clear();

            // 显示 diff 内容
            Text text = new Text(formatDiff(diff));
            text.getStyleClass().add("content-panel__diff-content");
            contentContainer.getChildren().add(text);

            // 添加审核按钮栏
            HBox actionBar = createDiffActionBar(diff);
            contentContainer.getChildren().add(actionBar);

            show();
        });
    }

    /**
     * 恢复 ScrollPane 可见性（终端显示时会被隐藏）
     */
    private void restoreScrollPane() {
        contentScrollPane.setVisible(true);
        contentScrollPane.setManaged(true);
        // 移除可能残留的终端视图
        contentPanel.getChildren().removeIf(node -> node instanceof JediTerminalView);
    }

    /**
     * 创建 diff 审核按钮栏
     */
    private HBox createDiffActionBar(FileDiff diff) {
        HBox actionBar = new HBox();
        actionBar.getStyleClass().add("content-panel__diff-actions");
        actionBar.setAlignment(Pos.CENTER_RIGHT);
        actionBar.setSpacing(8);
        actionBar.setPadding(new Insets(8, 0, 0, 0));

        // 确定按钮
        Button approveBtn = new Button("确定");
        approveBtn.getStyleClass().add("content-panel__diff-btn--approve");
        approveBtn.setOnAction(e -> {
            diffService.approveDiff(diff.id());
            hide();
        });

        // 撤销按钮
        Button rejectBtn = new Button("撤销");
        rejectBtn.getStyleClass().add("content-panel__diff-btn--reject");
        rejectBtn.setOnAction(e -> {
            diffService.rejectDiff(diff.id());
            hide();
        });

        actionBar.getChildren().addAll(approveBtn, rejectBtn);
        return actionBar;
    }

    /**
     * 格式化 diff 内容
     */
    private String formatDiff(FileDiff diff) {
        StringBuilder sb = new StringBuilder();
        sb.append("文件: ").append(diff.filePath()).append("\n");
        if (diff.isCreate()) sb.append("(新建文件)\n");
        if (diff.isDelete()) sb.append("(删除文件)\n");
        sb.append("\n");
        for (FileDiff.Hunk hunk : diff.hunks()) {
            sb.append("@@ -").append(hunk.oldStart()).append(",").append(hunk.oldCount())
              .append(" +").append(hunk.newStart()).append(",").append(hunk.newCount()).append(" @@\n");
            for (FileDiff.DiffLine line : hunk.lines()) {
                String prefix = switch (line.type()) {
                    case ADD -> "+";
                    case REMOVE -> "-";
                    case CONTEXT -> " ";
                };
                sb.append(prefix).append(line.content()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 显示加载状态内容
     */
    public void showLoadingContent(String message) {
        Platform.runLater(() -> {
            restoreScrollPane();
            contentTitle.setText("加载中");
            contentContainer.getChildren().clear();
            ProgressIndicator indicator = new ProgressIndicator();
            indicator.setPrefSize(32, 32);
            Label label = new Label(message);
            label.getStyleClass().add("content-panel__loading-text");
            VBox loadingBox = new VBox(indicator, label);
            loadingBox.setAlignment(Pos.CENTER);
            loadingBox.setSpacing(8);
            contentContainer.getChildren().add(loadingBox);
            show();
        });
    }

    /**
     * 显示错误信息
     */
    public void showErrorContent(String message, Runnable retryAction) {
        Platform.runLater(() -> {
            restoreScrollPane();
            contentTitle.setText("错误");
            contentContainer.getChildren().clear();
            Label errorLabel = new Label(message);
            errorLabel.getStyleClass().add("content-panel__error-text");
            errorLabel.setWrapText(true);
            contentContainer.getChildren().add(errorLabel);

            if (retryAction != null) {
                Button retryBtn = new Button("重试");
                retryBtn.getStyleClass().add("content-panel__retry-btn");
                retryBtn.setOnAction(e -> retryAction.run());
                contentContainer.getChildren().add(retryBtn);
            }
            show();
        });
    }

    /**
     * 打开终端面板
     * 终端直接添加到 contentPanel（不通过 ScrollPane），因为终端自己管理滚动
     */
    public void openTerminal(Path workingDir) {
        this.lastTerminalWorkingDir = workingDir;
        // 显示加载状态
        showLoadingContent("正在启动终端...");

        // 异步创建终端会话
        new Thread(() -> {
            try {
                // 关闭已有终端
                closeTerminalInternal();

                // 创建新终端
                terminalSession = ptyTerminalService.createSession(workingDir);
                terminalView = new JediTerminalView();
                terminalView.startSession(terminalSession);

                Platform.runLater(() -> {
                    contentTitle.setText("终端");
                    // 隐藏 ScrollPane，终端自己管理滚动
                    contentScrollPane.setVisible(false);
                    contentScrollPane.setManaged(false);
                    // 移除之前可能残留的终端视图
                    contentPanel.getChildren().removeIf(node -> node instanceof JediTerminalView);
                    // 将终端直接添加到 contentPanel（在 header 之后）
                    VBox.setVgrow(terminalView, Priority.ALWAYS);
                    contentPanel.getChildren().add(terminalView);
                    show();
                    // 请求焦点确保终端可交互
                    Platform.runLater(() -> terminalView.requestFocus());
                });
            } catch (IOException e) {
                log.error("创建终端会话失败", e);
                Platform.runLater(() -> showErrorContent("终端启动失败: " + e.getMessage(),
                        () -> openTerminal(lastTerminalWorkingDir)));
            } catch (Exception e) {
                log.error("终端初始化异常", e);
                Platform.runLater(() -> showErrorContent("终端初始化异常: " + e.getMessage(),
                        () -> openTerminal(lastTerminalWorkingDir)));
            }
        }).start();
    }

    /**
     * 关闭终端面板
     */
    public void closeTerminal() {
        closeTerminalInternal();
    }

    /**
     * 内部关闭终端方法（不更新 UI）
     */
    private void closeTerminalInternal() {
        if (terminalView != null) {
            terminalView.closeSession();
            terminalView = null;
        }
        if (terminalSession != null) {
            ptyTerminalService.closeSession(terminalSession.getSessionId());
            terminalSession = null;
        }
    }

    @FXML
    private void handleCloseContent() {
        closeTerminal();
        restoreScrollPane();
        hide();
    }
}
