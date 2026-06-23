package cn.bitloom.node.tool;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.LinkedHashSet;
import java.util.Set;

public class ToolGroupCard extends VBox {

    private final VBox body;
    private final Label countLabel;
    private final Label namesLabel;
    private final Set<String> toolNames = new LinkedHashSet<>();
    private boolean expanded = false;
    private int toolCount = 0;

    public ToolGroupCard() {
        this.getStyleClass().add("chat-message");
        this.getStyleClass().add("chat-message--tool-group");

        // Header
        HBox header = new HBox();
        header.getStyleClass().add("chat-message__tool-group-header");

        countLabel = new Label("0 个工具调用");
        countLabel.getStyleClass().add("chat-message__tool-group-count");

        Label separatorLabel = new Label("·");
        separatorLabel.getStyleClass().add("chat-message__tool-group-separator");

        namesLabel = new Label("");
        namesLabel.getStyleClass().add("chat-message__tool-group-names");

        header.getChildren().addAll(countLabel, separatorLabel, namesLabel);
        this.getChildren().add(header);

        // Body (collapsible)
        body = new VBox();
        body.getStyleClass().add("chat-message__tool-group-body");
        body.setVisible(false);
        body.setManaged(false);
        this.getChildren().add(body);

        // Click to toggle
        header.setOnMouseClicked(e -> toggle());
    }

    public void addToolCard(Node toolCard, String toolName) {
        body.getChildren().add(toolCard);
        toolCount++;
        if (toolName != null && !toolName.isEmpty()) {
            toolNames.add(toolName);
        }
        updateSummary();
    }

    public int getToolCount() {
        return toolCount;
    }

    private void updateSummary() {
        countLabel.setText(toolCount + " 个工具调用");
        var nameList = toolNames.stream().limit(3).toList();
        String namesText = String.join(" · ", nameList);
        if (toolNames.size() > 3) {
            namesText += " ...";
        }
        namesLabel.setText(namesText);
    }

    private void toggle() {
        expanded = !expanded;
        if (expanded) {
            expand();
        } else {
            collapse();
        }
    }

    private void expand() {
        body.setVisible(true);
        body.setManaged(true);
    }

    private void collapse() {
        body.setVisible(false);
        body.setManaged(false);
    }
}
