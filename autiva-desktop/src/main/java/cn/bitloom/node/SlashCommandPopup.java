package cn.bitloom.node;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Slash 命令建议浮层（Apple 风格）：输入以 / 开头时在输入框上方弹出，
 * 前缀过滤，↑↓ 选择、Enter 确认、Esc 关闭、点击选择。
 *
 * <p>仅当整段文本形如 {@code /xxx}（命令名输入中，尚未输入参数/空格）时显示。
 * 选中带参数命令时回调 onPick 由调用方决定补全或直接执行。
 */
public class SlashCommandPopup {

    private static final double ROW_HEIGHT = 40;
    private static final double POPUP_WIDTH = 380;

    private final Popup popup = new Popup();
    private final VBox list = new VBox(2);
    private final List<SlashCommands.Command> commands;
    private final Consumer<SlashCommands.Command> onPick;
    private final List<Label> items = new ArrayList<>();
    private List<SlashCommands.Command> filtered = List.of();
    private int selectedIndex = -1;

    public SlashCommandPopup(List<SlashCommands.Command> commands, Consumer<SlashCommands.Command> onPick) {
        this.commands = commands;
        this.onPick = onPick;

        list.setPadding(new Insets(6));
        list.setStyle("""
                -fx-background-color: #ffffff;
                -fx-background-radius: 12;
                -fx-border-radius: 12;
                -fx-border-color: rgba(0, 0, 0, 0.08);
                -fx-border-width: 1;
                -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.12), 16, 0, 0, 6);
                """);
        list.setPrefWidth(POPUP_WIDTH);
        list.setMaxWidth(POPUP_WIDTH);

        popup.getContent().add(list);
        popup.setAutoHide(true);
        popup.setHideOnEscape(false); // Esc 由 handleKeyEvent 处理，保持键盘语义一致
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    public void hide() {
        popup.hide();
    }

    /**
     * 根据输入框文本更新浮层：形如 /xxx 时显示过滤结果，否则隐藏。
     *
     * @param owner 输入框（锚定在其上方）
     * @param text  当前文本
     */
    public void update(javafx.scene.control.TextInputControl owner, String text) {
        if (text == null || !text.matches("/[a-zA-Z]*")) {
            hide();
            return;
        }
        String prefix = text.substring(1).toLowerCase();
        filtered = commands.stream()
                .filter(c -> c.name().startsWith(prefix))
                .toList();
        if (filtered.isEmpty()) {
            hide();
            return;
        }

        list.getChildren().clear();
        items.clear();
        for (SlashCommands.Command command : filtered) {
            Label item = new Label(command.fullName() + "    " + command.description());
            item.setMaxWidth(Double.MAX_VALUE);
            item.setMinHeight(ROW_HEIGHT - 8);
            item.setPadding(new Insets(0, 10, 0, 10));
            item.setWrapText(true);
            item.setStyle(itemStyle(false));
            item.setOnMouseClicked(e -> pick(command));
            items.add(item);
            list.getChildren().add(item);
        }
        select(0);

        double height = filtered.size() * ROW_HEIGHT + 12;
        var anchor = owner.localToScreen(0, 0);
        if (anchor != null && owner.getScene() != null && owner.getScene().getWindow() != null) {
            popup.show(owner.getScene().getWindow(), anchor.getX(), anchor.getY() - height - 6);
        }
    }

    /**
     * 键盘事件处理：↑↓ 移动、Enter 确认、Esc 关闭。
     *
     * @return true 表示事件已消费（调用方无需再处理）
     */
    public boolean handleKeyEvent(KeyEvent event) {
        if (!popup.isShowing()) {
            return false;
        }
        return switch (event.getCode()) {
            case DOWN -> {
                select(Math.min(selectedIndex + 1, filtered.size() - 1));
                yield true;
            }
            case UP -> {
                select(Math.max(selectedIndex - 1, 0));
                yield true;
            }
            case ENTER -> {
                if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
                    pick(filtered.get(selectedIndex));
                }
                yield true;
            }
            case ESCAPE -> {
                hide();
                yield true;
            }
            default -> false;
        };
    }

    private void pick(SlashCommands.Command command) {
        hide();
        onPick.accept(command);
    }

    private void select(int index) {
        selectedIndex = index;
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setStyle(itemStyle(i == index));
        }
        if (index >= 0 && index < items.size()) {
            Region selected = items.get(index);
            selected.requestLayout();
        }
    }

    /** 选中态：浅蓝半透明背景 + 蓝字（与 tab 选中态一致） */
    private String itemStyle(boolean selected) {
        return selected
                ? "-fx-background-color: rgba(0,113,227,0.1); -fx-background-radius: 8; "
                        + "-fx-text-fill: #0071e3; -fx-font-size: 13px;"
                : "-fx-background-color: transparent; -fx-text-fill: #1d1d1f; -fx-font-size: 13px;";
    }
}
