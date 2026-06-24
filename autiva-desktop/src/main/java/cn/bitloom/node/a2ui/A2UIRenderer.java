package cn.bitloom.node.a2ui;

import cn.bitloom.agentic.a2ui.A2UIAction;
import cn.bitloom.agentic.a2ui.A2UIComponent;
import cn.bitloom.agentic.a2ui.A2UIComponentType;
import cn.bitloom.agentic.a2ui.A2UICheck;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A2UI JavaFX 渲染器。
 * <p>
 * 将 A2UI 组件树渲染为 JavaFX Node,每个 Basic Catalog 组件一个渲染方法。
 * 支持数据绑定(JSON Pointer)、事件绑定(Event/FunctionCall)、验证规则(Checks)。
 */
@Slf4j
public class A2UIRenderer {

    private final A2UISurface surface;

    public A2UIRenderer(A2UISurface surface) {
        this.surface = surface;
    }

    /**
     * 渲染组件,返回 JavaFX Node。
     */
    public Node render(A2UIComponent component) {
        try {
            Node node = switch (component.component()) {
                case ROW -> renderRow(component);
                case COLUMN -> renderColumn(component);
                case LIST -> renderList(component);
                case TEXT -> renderText(component);
                case IMAGE -> renderImage(component);
                case ICON -> renderIcon(component);
                case DIVIDER -> renderDivider(component);
                case BUTTON -> renderButton(component);
                case TEXT_FIELD -> renderTextField(component);
                case CHECK_BOX -> renderCheckBox(component);
                case SLIDER -> renderSlider(component);
                case DATE_TIME_INPUT -> renderDateTimeInput(component);
                case CHOICE_PICKER -> renderChoicePicker(component);
                case TABS -> renderTabs(component);
                case CARD -> renderCard(component);
            };
            return node;
        } catch (Exception e) {
            log.error("Failed to render component {}: {}", component.id(), e.getMessage(), e);
            Label errorLabel = new Label("渲染失败: " + e.getMessage());
            errorLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12;");
            return errorLabel;
        }
    }

    // ===== 布局组件 =====

    private Node renderRow(A2UIComponent component) {
        HBox row = new HBox();
        row.getStyleClass().add("a2ui-row");
        row.setSpacing(8);
        row.setAlignment(Pos.CENTER_LEFT);

        applyLayoutProperties(row, component);

        for (String childId : component.getChildren()) {
            A2UIComponent child = surface.getComponent(childId);
            if (child != null) {
                row.getChildren().add(render(child));
            }
        }
        return row;
    }

    private Node renderColumn(A2UIComponent component) {
        VBox column = new VBox();
        column.getStyleClass().add("a2ui-column");
        column.setSpacing(8);
        column.setAlignment(Pos.TOP_LEFT);

        applyLayoutProperties(column, component);

        for (String childId : component.getChildren()) {
            A2UIComponent child = surface.getComponent(childId);
            if (child != null) {
                column.getChildren().add(render(child));
            }
        }
        return column;
    }

    private Node renderList(A2UIComponent component) {
        VBox list = new VBox();
        list.getStyleClass().add("a2ui-list");
        list.setSpacing(4);

        for (String childId : component.getChildren()) {
            A2UIComponent child = surface.getComponent(childId);
            if (child != null) {
                list.getChildren().add(render(child));
            }
        }

        ScrollPane scrollPane = new ScrollPane(list);
        scrollPane.getStyleClass().add("a2ui-list-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setMaxHeight(300);
        return scrollPane;
    }

    // ===== 显示组件 =====

    private Node renderText(A2UIComponent component) {
        Label text = new Label();
        text.getStyleClass().add("a2ui-text");

        String content = component.getString("text");
        Object resolved = surface.resolveValue(content);
        text.setText(resolved != null ? resolved.toString() : "");

        String variant = component.getString("variant");
        if (variant != null) {
            text.getStyleClass().add("a2ui-text--" + variant);
            applyTextVariant(text, variant);
        }

        text.setWrapText(true);
        return text;
    }

    private void applyTextVariant(Label label, String variant) {
        switch (variant) {
            case "h1" -> label.setFont(Font.font("SF Pro Text", FontWeight.BOLD, 28));
            case "h2" -> label.setFont(Font.font("SF Pro Text", FontWeight.BOLD, 22));
            case "h3" -> label.setFont(Font.font("SF Pro Text", FontWeight.BOLD, 18));
            case "h4" -> label.setFont(Font.font("SF Pro Text", FontWeight.BOLD, 16));
            case "h5" -> label.setFont(Font.font("SF Pro Text", FontWeight.BOLD, 14));
            case "caption" -> {
                label.setFont(Font.font("SF Pro Text", 12));
                label.setTextFill(Color.web("#86868b"));
            }
            default -> label.setFont(Font.font("SF Pro Text", 15));
        }
    }

    private Node renderImage(A2UIComponent component) {
        ImageView imageView = new ImageView();
        imageView.getStyleClass().add("a2ui-image");
        imageView.setFitWidth(200);
        imageView.setFitHeight(150);
        imageView.setPreserveRatio(true);

        String url = component.getString("url");
        Object resolved = surface.resolveValue(url);
        if (resolved != null) {
            try {
                imageView.setImage(new Image(resolved.toString()));
            } catch (Exception e) {
                log.warn("Failed to load image: {}", resolved);
            }
        }

        String fit = component.getString("fit");
        if ("cover".equals(fit)) {
            imageView.setPreserveRatio(false);
        }

        return imageView;
    }

    private Node renderIcon(A2UIComponent component) {
        Label icon = new Label();
        icon.getStyleClass().add("a2ui-icon");

        String name = component.getString("name");
        Object resolved = surface.resolveValue(name);
        if (resolved != null) {
            // 简单图标映射(后续可扩展)
            icon.setText(getIconEmoji(resolved.toString()));
            icon.setFont(Font.font(20));
        }
        return icon;
    }

    private String getIconEmoji(String name) {
        return switch (name.toLowerCase()) {
            case "check" -> "✓";
            case "close", "x" -> "✕";
            case "warning" -> "⚠";
            case "info" -> "ℹ";
            case "arrow_right" -> "→";
            case "arrow_left" -> "←";
            case "arrow_up" -> "↑";
            case "arrow_down" -> "↓";
            default -> "•";
        };
    }

    private Node renderDivider(A2UIComponent component) {
        Region divider = new Region();
        divider.getStyleClass().add("a2ui-divider");

        String axis = component.getString("axis");
        if ("vertical".equals(axis)) {
            divider.setPrefWidth(1);
            divider.setPrefHeight(Region.USE_COMPUTED_SIZE);
            HBox.setHgrow(divider, Priority.NEVER);
            VBox.setVgrow(divider, Priority.ALWAYS);
        } else {
            divider.setPrefHeight(1);
            divider.setPrefWidth(Region.USE_COMPUTED_SIZE);
            HBox.setHgrow(divider, Priority.ALWAYS);
        }
        return divider;
    }

    // ===== 交互组件 =====

    private Node renderButton(A2UIComponent component) {
        Button button = new Button();
        button.getStyleClass().add("a2ui-button");

        // 渲染子组件作为按钮内容
        String childId = component.getString("child");
        if (childId != null) {
            A2UIComponent child = surface.getComponent(childId);
            if (child != null) {
                Node childNode = render(child);
                if (childNode instanceof Label label) {
                    button.setText(label.getText());
                } else {
                    button.setGraphic(childNode);
                }
            }
        }

        // variant 样式
        String variant = component.getString("variant");
        if (variant != null) {
            button.getStyleClass().add("a2ui-button--" + variant);
        }

        // 验证规则
        if (component.checks() != null && !component.checks().isEmpty()) {
            boolean allPassed = true;
            for (A2UICheck check : component.checks()) {
                if (!surface.evaluateCheck(check)) {
                    allPassed = false;
                    break;
                }
            }
            button.setDisable(!allPassed);
        }

        // 绑定 action
        if (component.action() != null) {
            button.setOnAction(e -> handleAction(component.action(), component.id()));
        }

        return button;
    }

    private Node renderTextField(A2UIComponent component) {
        TextField textField = new TextField();
        textField.getStyleClass().add("a2ui-text-field");

        String label = component.getString("label");
        if (label != null) {
            textField.setPromptText(label);
        }

        // 数据绑定
        Object value = component.get("value");
        Object resolved = surface.resolveValue(value);
        if (resolved != null) {
            textField.setText(resolved.toString());
        }

        // textFieldType
        String type = component.getString("textFieldType");
        if ("number".equals(type)) {
            // 数字输入限制(简化实现)
            textField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal.matches("\\d*\\.?\\d*")) {
                    textField.setText(oldVal);
                }
            });
        } else if ("obscured".equals(type)) {
            // 密码输入(使用 PasswordField 替代)
            PasswordField passwordField = new PasswordField();
            passwordField.getStyleClass().add("a2ui-text-field");
            if (label != null) passwordField.setPromptText(label);
            if (resolved != null) passwordField.setText(resolved.toString());
            bindInputToDataModel(passwordField.textProperty(), component.get("value"), component.id());
            return passwordField;
        }

        bindInputToDataModel(textField.textProperty(), component.get("value"), component.id());
        return textField;
    }

    private Node renderCheckBox(A2UIComponent component) {
        CheckBox checkBox = new CheckBox();
        checkBox.getStyleClass().add("a2ui-check-box");

        String label = component.getString("label");
        if (label != null) {
            checkBox.setText(label);
        }

        Object value = component.get("value");
        Object resolved = surface.resolveValue(value);
        if (resolved instanceof Boolean bool) {
            checkBox.setSelected(bool);
        }

        bindInputToDataModel(checkBox.selectedProperty(), component.get("value"), component.id());
        return checkBox;
    }

    private Node renderSlider(A2UIComponent component) {
        Slider slider = new Slider();
        slider.getStyleClass().add("a2ui-slider");

        Object minVal = component.get("minValue");
        if (minVal instanceof Number num) {
            slider.setMin(num.doubleValue());
        }

        Object maxVal = component.get("maxValue");
        if (maxVal instanceof Number num) {
            slider.setMax(num.doubleValue());
        }

        Object value = component.get("value");
        Object resolved = surface.resolveValue(value);
        if (resolved instanceof Number num) {
            slider.setValue(num.doubleValue());
        }

        bindInputToDataModel(slider.valueProperty(), component.get("value"), component.id());
        return slider;
    }

    private Node renderDateTimeInput(A2UIComponent component) {
        DatePicker datePicker = new DatePicker();
        datePicker.getStyleClass().add("a2ui-date-time-input");

        Object value = component.get("value");
        Object resolved = surface.resolveValue(value);
        if (resolved instanceof String dateStr) {
            try {
                datePicker.setValue(java.time.LocalDate.parse(dateStr));
            } catch (Exception e) {
                log.warn("Failed to parse date: {}", dateStr);
            }
        }

        // 数据绑定
        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            String dateStr = newVal != null ? newVal.toString() : null;
            updateDataModelByPath("/" + component.id(), dateStr);
        });

        return datePicker;
    }

    private Node renderChoicePicker(A2UIComponent component) {
        VBox container = new VBox();
        container.getStyleClass().add("a2ui-choice-picker");
        container.setSpacing(4);

        // 解析选项
        Object optionsObj = component.get("options");
        int maxAllowed = 1;
        Object maxObj = component.get("maxAllowedSelections");
        if (maxObj instanceof Number num) {
            maxAllowed = num.intValue();
        }

        List<String> selectedValues = new ArrayList<>();

        if (maxAllowed == 1) {
            // 单选模式：使用 RadioButton
            ToggleGroup group = new ToggleGroup();
            if (optionsObj instanceof List<?> options) {
                for (Object opt : options) {
                    if (opt instanceof Map<?, ?> optMap) {
                        String label = optMap.get("label") != null ? optMap.get("label").toString() : "";
                        String value = optMap.get("value") != null ? optMap.get("value").toString() : label;
                        String desc = optMap.get("description") != null ? optMap.get("description").toString() : null;

                        RadioButton rb = new RadioButton(label);
                        rb.getStyleClass().add("a2ui-choice-picker__option");
                        if (desc != null) rb.setTooltip(new Tooltip(desc));
                        rb.setToggleGroup(group);
                        container.getChildren().add(rb);

                        // 选中时更新数据模型
                        rb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                            if (newVal) {
                                updateDataModelByPath("/" + component.id(), value);
                            }
                        });
                    }
                }
            }
        } else {
            // 多选模式：使用 CheckBox
            if (optionsObj instanceof List<?> options) {
                for (Object opt : options) {
                    if (opt instanceof Map<?, ?> optMap) {
                        String label = optMap.get("label") != null ? optMap.get("label").toString() : "";
                        String value = optMap.get("value") != null ? optMap.get("value").toString() : label;
                        String desc = optMap.get("description") != null ? optMap.get("description").toString() : null;

                        CheckBox cb = new CheckBox(label);
                        cb.getStyleClass().add("a2ui-choice-picker__option");
                        if (desc != null) cb.setTooltip(new Tooltip(desc));
                        container.getChildren().add(cb);

                        // 选中时更新数据模型
                        cb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                            if (newVal) {
                                selectedValues.add(value);
                            } else {
                                selectedValues.remove(value);
                            }
                            updateDataModelByPath("/" + component.id(), new ArrayList<>(selectedValues));
                        });
                    }
                }
            }
        }

        return container;
    }

    // ===== 容器组件 =====

    private Node renderTabs(A2UIComponent component) {
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("a2ui-tabs");

        for (String childId : component.getChildren()) {
            A2UIComponent child = surface.getComponent(childId);
            if (child != null) {
                Tab tab = new Tab();
                String title = child.getString("title");
                tab.setText(title != null ? title : child.id());

                Node content = render(child);
                tab.setContent(content);
                tab.setClosable(false);
                tabPane.getTabs().add(tab);
            }
        }
        return tabPane;
    }

    private Node renderCard(A2UIComponent component) {
        VBox card = new VBox();
        card.getStyleClass().add("a2ui-card__inner");
        card.setSpacing(8);
        card.setPadding(new Insets(12));

        for (String childId : component.getChildren()) {
            A2UIComponent child = surface.getComponent(childId);
            if (child != null) {
                card.getChildren().add(render(child));
            }
        }
        return card;
    }

    // ===== 辅助方法 =====

    private void applyLayoutProperties(Pane pane, A2UIComponent component) {
        // 只有 HBox 和 VBox 有 setAlignment 方法，需要强制转换
        if (pane instanceof HBox hbox) {
            applyAlignment(hbox, component);
        } else if (pane instanceof VBox vbox) {
            applyAlignment(vbox, component);
        }
    }

    private void applyAlignment(HBox hbox, A2UIComponent component) {
        String justify = component.getString("justify");
        if (justify != null) {
            switch (justify) {
                case "center" -> hbox.setAlignment(Pos.CENTER);
                case "end" -> hbox.setAlignment(Pos.CENTER_RIGHT);
                case "spaceBetween" -> hbox.setAlignment(Pos.CENTER_LEFT);
            }
        }

        String align = component.getString("align");
        if (align != null) {
            switch (align) {
                case "center" -> hbox.setAlignment(Pos.CENTER);
                case "start" -> hbox.setAlignment(Pos.CENTER_LEFT);
                case "end" -> hbox.setAlignment(Pos.CENTER_RIGHT);
            }
        }
    }

    private void applyAlignment(VBox vbox, A2UIComponent component) {
        String justify = component.getString("justify");
        if (justify != null) {
            switch (justify) {
                case "center" -> vbox.setAlignment(Pos.CENTER);
                case "end" -> vbox.setAlignment(Pos.BOTTOM_RIGHT);
                case "spaceBetween" -> vbox.setAlignment(Pos.TOP_LEFT);
            }
        }

        String align = component.getString("align");
        if (align != null) {
            switch (align) {
                case "center" -> vbox.setAlignment(Pos.CENTER);
                case "start" -> vbox.setAlignment(Pos.TOP_LEFT);
                case "end" -> vbox.setAlignment(Pos.BOTTOM_RIGHT);
            }
        }
    }

    /**
     * 处理组件 action(Event 或 FunctionCall)。
     */
    private void handleAction(A2UIAction action, String componentId) {
        if (action instanceof A2UIAction.Event event) {
            // 解析 context 中的 path 引用
            Map<String, Object> resolvedContext = new HashMap<>();
            if (event.context() != null) {
                for (Map.Entry<String, Object> entry : event.context().entrySet()) {
                    resolvedContext.put(entry.getKey(), surface.resolveValue(entry.getValue()));
                }
            }
            // 将整个数据模型合并到 context 中，确保 Agent 能获取所有表单值
            Map<String, Object> fullDataModel = surface.getAllData();
            if (fullDataModel != null && !fullDataModel.isEmpty()) {
                // dataModel 中的值优先级低于 action.context 中显式指定的值
                for (Map.Entry<String, Object> entry : fullDataModel.entrySet()) {
                    resolvedContext.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
            // 回流到 Agent
            surface.fireUserAction(componentId, event.name(), resolvedContext);
        } else if (action instanceof A2UIAction.FunctionCall funcCall) {
            // 本地执行
            surface.executeFunction(funcCall.call(), funcCall.args());
        }
    }

    /**
     * 将输入组件的值绑定到数据模型。
     * <p>
     * 如果 valueSpec 是 {path: "/xxx"} 格式，绑定到指定路径。
     * 否则，使用组件 ID 作为 key 自动绑定到 dataModel 根级。
     */
    private void bindInputToDataModel(javafx.beans.property.Property<?> property, Object valueSpec, String componentId) {
        if (valueSpec instanceof Map<?, ?> map && map.containsKey("path")) {
            String path = map.get("path").toString();
            property.addListener((obs, oldVal, newVal) -> updateDataModelByPath(path, newVal));
        } else {
            // 自动绑定：用组件 ID 作为 key
            property.addListener((obs, oldVal, newVal) -> updateDataModelByPath("/" + componentId, newVal));
        }
    }

    @SuppressWarnings("unchecked")
    private void updateDataModelByPath(String path, Object value) {
        // 通过 surface 更新数据模型
        // 这里直接调用 surface 的内部方法
        surface.setByPathPublic(path, value);
    }
}
