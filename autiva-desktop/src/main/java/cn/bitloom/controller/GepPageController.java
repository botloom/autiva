package cn.bitloom.controller;

import cn.bitloom.agentic.evolve.EvolutionEngine;
import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.gene.Capsule;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneStore;
import cn.bitloom.agentic.evolve.solidify.EvolutionEvent;
import cn.bitloom.agentic.evolve.strategy.StrategyPreset;
import cn.bitloom.holder.PageHolder;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

@Slf4j
@Component
public class GepPageController implements Initializable, PageHolder {

    @Getter
    @Setter
    private IndexController indexController;

    @FXML private VBox gepPage;
    @FXML private ComboBox<StrategyPreset> strategyComboBox;
    @FXML private VBox genesContainer;
    @FXML private VBox capsulesContainer;
    @FXML private VBox eventsContainer;

    private final GeneStore geneStore;
    private final EvolutionEngine evolutionEngine;
    private final EvolveConfig evolveConfig;

    public GepPageController(GeneStore geneStore, EvolutionEngine evolutionEngine, EvolveConfig evolveConfig) {
        this.geneStore = geneStore;
        this.evolutionEngine = evolutionEngine;
        this.evolveConfig = evolveConfig;
    }

    @FXML
    public void initialize(URL location, ResourceBundle resources) {
        strategyComboBox.setItems(FXCollections.observableArrayList(StrategyPreset.values()));
        strategyComboBox.setValue(evolveConfig.getStrategyPreset());
        strategyComboBox.setOnAction(e -> {
            StrategyPreset selected = strategyComboBox.getValue();
            evolveConfig.setStrategyPreset(selected);
        });

        refreshContent();
    }

    @FXML
    public void runEvolutionCycle() {
        log.info("[GEP] 用户触发进化周期");
        try {
            EvolutionEngine.EvolutionCycleResult result = evolutionEngine.runCycle(List.of());
            if (result.success()) {
                showInfo("进化周期完成", "选中基因: " + result.gene().id() + "\n策略: " + result.preset());
            } else {
                showInfo("进化周期", "未产生结果: " + result.reason());
            }
        } catch (Exception e) {
            showError("进化周期失败", e.getMessage());
        }
        refreshContent();
    }

    private void refreshContent() {
        renderGenes();
        renderCapsules();
        renderEvents();
    }

    // ========== Genes ==========

    private void renderGenes() {
        genesContainer.getChildren().clear();

        List<Gene> genes = geneStore.loadGenes();
        if (genes.isEmpty()) {
            Label empty = new Label("暂无基因数据");
            empty.getStyleClass().add("gep-page__empty");
            genesContainer.getChildren().add(empty);
        } else {
            for (Gene gene : genes) {
                genesContainer.getChildren().add(createGeneItem(gene));
            }
        }
    }

    private VBox createGeneItem(Gene gene) {
        VBox item = new VBox(6);
        item.getStyleClass().add("gep-page__gene-item");

        // 第一行：ID + 标签
        HBox header = new HBox(8);
        header.getStyleClass().add("gep-page__gene-header");

        Label idLabel = new Label(gene.id());
        idLabel.getStyleClass().add("gep-page__gene-id");

        Label categoryLabel = new Label(gene.category().code());
        categoryLabel.getStyleClass().addAll("gep-page__gene-tag",
                "gep-page__gene-tag--" + gene.category().name());

        Label statusLabel = new Label(gene.enabled() ? "启用" : "禁用");
        statusLabel.getStyleClass().addAll("gep-page__gene-tag",
                gene.enabled() ? "gep-page__gene-tag--enabled" : "gep-page__gene-tag--disabled");

        Label boostLabel = new Label(String.format("boost=%.2f", gene.epigeneticBoost()));
        boostLabel.getStyleClass().add("gep-page__gene-boost");

        header.getChildren().addAll(idLabel, categoryLabel, statusLabel, boostLabel);

        // 第二行：摘要
        Label summaryLabel = new Label(gene.summary());
        summaryLabel.getStyleClass().add("gep-page__gene-summary");
        summaryLabel.setWrapText(true);

        // 第三行：信号 + 操作
        HBox footer = new HBox(8);
        footer.getStyleClass().add("gep-page__gene-footer");

        Label signalsLabel = new Label(String.join(", ", gene.signalsMatch()));
        signalsLabel.getStyleClass().add("gep-page__gene-signals");
        signalsLabel.setWrapText(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        HBox actions = new HBox(6);
        actions.getStyleClass().add("gep-page__gene-actions");

        Button toggleBtn = new Button(gene.enabled() ? "禁用" : "启用");
        toggleBtn.getStyleClass().add("gep-page__gene-action-btn");
        toggleBtn.setOnAction(e -> {
            geneStore.toggleGene(gene.id(), !gene.enabled());
            refreshContent();
        });

        Button deleteBtn = new Button("删除");
        deleteBtn.getStyleClass().addAll("gep-page__gene-action-btn", "gep-page__gene-action-btn--danger");
        deleteBtn.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("确认删除");
            alert.setHeaderText("删除基因: " + gene.id());
            alert.setContentText("此操作不可恢复，确定要删除吗？");
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    geneStore.deleteGene(gene.id());
                    refreshContent();
                }
            });
        });

        actions.getChildren().addAll(toggleBtn, deleteBtn);
        footer.getChildren().addAll(signalsLabel, spacer, actions);

        item.getChildren().addAll(header, summaryLabel, footer);
        return item;
    }

    // ========== Capsules ==========

    private void renderCapsules() {
        capsulesContainer.getChildren().clear();

        List<Capsule> capsules = geneStore.loadCapsules();
        if (capsules.isEmpty()) {
            Label empty = new Label("暂无胶囊数据");
            empty.getStyleClass().add("gep-page__empty");
            capsulesContainer.getChildren().add(empty);
        } else {
            for (Capsule capsule : capsules) {
                capsulesContainer.getChildren().add(createCapsuleItem(capsule));
            }
        }
    }

    private VBox createCapsuleItem(Capsule capsule) {
        VBox item = new VBox(8);
        item.getStyleClass().add("gep-page__capsule-item");

        HBox header = new HBox(8);
        header.getStyleClass().add("gep-page__capsule-header");

        Label idLabel = new Label(capsule.id());
        idLabel.getStyleClass().add("gep-page__capsule-id");

        Label scoreLabel = new Label(String.format("分数=%.2f", capsule.score()));
        scoreLabel.getStyleClass().add("gep-page__capsule-score");

        header.getChildren().addAll(idLabel, scoreLabel);

        Label genesLabel = new Label("包含基因: " + String.join(", ", capsule.geneIds()));
        genesLabel.getStyleClass().add("gep-page__capsule-genes");
        genesLabel.setWrapText(true);

        HBox actions = new HBox(8);
        actions.getStyleClass().add("gep-page__capsule-actions");

        Button deleteBtn = new Button("删除");
        deleteBtn.getStyleClass().addAll("gep-page__gene-action-btn", "gep-page__gene-action-btn--danger");
        deleteBtn.setOnAction(e -> {
            geneStore.deleteCapsule(capsule.id());
            refreshContent();
        });

        actions.getChildren().add(deleteBtn);

        item.getChildren().addAll(header, genesLabel, actions);
        return item;
    }

    // ========== Events ==========

    private void renderEvents() {
        eventsContainer.getChildren().clear();

        List<EvolutionEvent> events = geneStore.readRecentEvents(20);
        if (events.isEmpty()) {
            Label empty = new Label("暂无进化事件记录");
            empty.getStyleClass().add("gep-page__empty");
            eventsContainer.getChildren().add(empty);
        } else {
            List<EvolutionEvent> reversedEvents = new java.util.ArrayList<>(events);
            java.util.Collections.reverse(reversedEvents);
            for (EvolutionEvent event : reversedEvents) {
                eventsContainer.getChildren().add(createEventItem(event));
            }
        }
    }

    private VBox createEventItem(EvolutionEvent event) {
        VBox item = new VBox(6);
        item.getStyleClass().add("gep-page__event-item");

        HBox header = new HBox(8);
        header.getStyleClass().add("gep-page__event-header");

        String statusText = event.outcome() != null ? event.outcome().status() : "unknown";
        Label statusLabel = new Label(statusText);
        statusLabel.getStyleClass().addAll("gep-page__event-tag", getEventTagModifier(statusText));

        Label idLabel = new Label(event.id());
        idLabel.getStyleClass().add("gep-page__event-id");

        header.getChildren().addAll(statusLabel, idLabel);

        String details = String.format("基因: %s | 意图: %s | 分数: %.2f",
                event.geneId() != null ? event.geneId() : "none",
                event.intent() != null ? event.intent() : "",
                event.outcome() != null ? event.outcome().score() : 0);
        Label detailsLabel = new Label(details);
        detailsLabel.getStyleClass().add("gep-page__event-details");
        detailsLabel.setWrapText(true);

        item.getChildren().addAll(header, detailsLabel);

        if (event.timestamp() > 0) {
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(event.timestamp()), ZoneId.systemDefault());
            String timeStr = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Label timeLabel = new Label(timeStr);
            timeLabel.getStyleClass().add("gep-page__event-time");
            item.getChildren().add(timeLabel);
        }

        return item;
    }

    private String getEventTagModifier(String status) {
        if (status == null) return "gep-page__event-tag--unknown";
        return switch (status.toLowerCase()) {
            case "success" -> "gep-page__event-tag--success";
            case "pending" -> "gep-page__event-tag--pending";
            case "failed" -> "gep-page__event-tag--failed";
            default -> "gep-page__event-tag--unknown";
        };
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @Override
    public void show() {
        this.gepPage.setVisible(true);
        this.gepPage.setManaged(true);
        refreshContent();
    }

    @Override
    public void hide() {
        this.gepPage.setVisible(false);
        this.gepPage.setManaged(false);
    }
}
