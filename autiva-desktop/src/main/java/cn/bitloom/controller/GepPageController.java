package cn.bitloom.controller;

import cn.bitloom.agentic.evolve.EvolutionEngine;
import cn.bitloom.agentic.evolve.experience.Experience;
import cn.bitloom.agentic.evolve.gene.Capsule;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.memory.MemoryRule;
import cn.bitloom.agentic.evolve.repository.GeneRepository;
import cn.bitloom.agentic.evolve.routing.RoutingEntry;
import cn.bitloom.agentic.evolve.solidify.EvolutionEvent;
import cn.bitloom.agentic.evolve.strategy.StrategyPreset;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.vm.GepPageViewModel;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class GepPageController implements Initializable, PageHolder {

    @Getter @Setter
    private IndexController indexController;

    @FXML private VBox gepPage;
    @FXML private VBox contentContainer;

    private final GepPageViewModel viewModel;
    private final Set<String> expandedGeneIds = new HashSet<>();

    public GepPageController(GepPageViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        refreshContent();
    }

    private void refreshContent() {
        viewModel.loadData();
        contentContainer.getChildren().clear();
        contentContainer.getChildren().add(renderActionCard());
        contentContainer.getChildren().add(renderOverviewCard());

        Label genesLabel = new Label("基因库");
        genesLabel.getStyleClass().add("gep-page__section-label");
        contentContainer.getChildren().add(genesLabel);

        List<Gene> genes = viewModel.getGenes();
        if (genes.isEmpty()) {
            contentContainer.getChildren().add(createEmptyCard("暂无基因数据"));
        } else {
            for (Gene gene : genes) {
                contentContainer.getChildren().add(renderGeneCard(gene));
            }
        }

        contentContainer.getChildren().add(renderRoutingCard());
        contentContainer.getChildren().add(renderMemoryCard());
        contentContainer.getChildren().add(renderEventsCard());

        List<Capsule> capsules = viewModel.getCapsules();
        if (!capsules.isEmpty()) {
            contentContainer.getChildren().add(renderCapsulesCard(capsules));
        }
    }

    // ========== Action Card ==========

    private VBox renderActionCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("gep-page__card");

        HBox row = new HBox(8);
        row.getStyleClass().add("gep-page__row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label strategyLabel = new Label("策略");
        strategyLabel.getStyleClass().add("gep-page__row-title");

        ComboBox<StrategyPreset> strategyCombo = new ComboBox<>(
                FXCollections.observableArrayList(StrategyPreset.values()));
        strategyCombo.setValue(viewModel.getStrategyPreset());
        strategyCombo.getStyleClass().add("gep-page__strategy-select");
        strategyCombo.setOnAction(e -> viewModel.setStrategyPreset(strategyCombo.getValue()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button cycleBtn = new Button("执行周期");
        cycleBtn.getStyleClass().add("gep-page__btn");
        cycleBtn.setOnAction(e -> {
            try {
                EvolutionEngine.EvolutionCycleResult result = viewModel.runEvolutionCycle();
                if (result.success()) {
                    showInfo("进化周期完成", "选中基因: " + result.gene().id());
                } else {
                    showInfo("进化周期", "未产生结果: " + result.reason());
                }
            } catch (Exception ex) {
                showError("进化周期失败", ex.getMessage());
            }
        });

        Button extractBtn = new Button("提取经验");
        extractBtn.getStyleClass().add("gep-page__btn");
        extractBtn.setOnAction(e -> {
            try {
                List<Experience> experiences = viewModel.extractAndEvolve();
                if (experiences.isEmpty()) {
                    showInfo("经验提取", "未提取到可操作的经验");
                } else {
                    StringBuilder sb = new StringBuilder("提取到 " + experiences.size() + " 条经验:\n\n");
                    for (Experience exp : experiences) {
                        sb.append("- ").append(exp.pattern())
                          .append(" (").append(exp.target()).append(", ")
                          .append(String.format("%.0f%%", exp.confidence() * 100)).append(")\n");
                    }
                    showInfo("经验提取完成", sb.toString());
                }
            } catch (Exception ex) {
                showError("经验提取失败", ex.getMessage());
            }
        });

        row.getChildren().addAll(strategyLabel, strategyCombo, spacer, cycleBtn, extractBtn);
        card.getChildren().add(row);
        return card;
    }

    // ========== Overview Card ==========

    private VBox renderOverviewCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("gep-page__card");

        card.getChildren().add(createOverviewRow("基因", String.valueOf(viewModel.getGeneCount().get()),
                "启用 " + viewModel.getEnabledGeneCount().get()));
        card.getChildren().add(createOverviewRow("事件", String.valueOf(viewModel.getEventCount().get()),
                String.format("成功率 %.0f%%", viewModel.getSuccessRate().get() * 100)));
        card.getChildren().add(createOverviewRow("路由", String.valueOf(viewModel.getRouteCount().get()), ""));
        card.getChildren().add(createOverviewRow("规则", String.valueOf(viewModel.getRuleCount().get()), ""));

        return card;
    }

    private HBox createOverviewRow(String title, String value, String subtitle) {
        HBox row = new HBox(8);
        row.getStyleClass().add("gep-page__row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("gep-page__row-title");
        titleLabel.setPrefWidth(60);

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("gep-page__row-title");

        if (!subtitle.isEmpty()) {
            Label subLabel = new Label(subtitle);
            subLabel.getStyleClass().add("gep-page__row-subtitle");
            row.getChildren().addAll(titleLabel, valueLabel, subLabel);
        } else {
            row.getChildren().addAll(titleLabel, valueLabel);
        }

        return row;
    }

    // ========== Gene Card ==========

    private VBox renderGeneCard(Gene gene) {
        VBox card = new VBox(12);
        card.getStyleClass().add("gep-page__card");

        boolean expanded = expandedGeneIds.contains(gene.id());

        // Header
        HBox header = new HBox(12);
        header.getStyleClass().add("gep-page__row");
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Label titleLabel = new Label(gene.id());
        titleLabel.getStyleClass().add("gep-page__card-title");

        Label descLabel = new Label(gene.summary());
        descLabel.getStyleClass().add("gep-page__card-description");

        titleBox.getChildren().addAll(titleLabel, descLabel);
        header.getChildren().add(titleBox);

        // Tags
        HBox tags = new HBox(6);
        tags.setAlignment(Pos.CENTER_LEFT);

        Label categoryTag = new Label(gene.category().code());
        categoryTag.getStyleClass().addAll("gep-page__tag", "gep-page__tag--" + gene.category().name());

        Label runtimeTag = new Label(gene.runtimeType().code());
        runtimeTag.getStyleClass().addAll("gep-page__tag", "gep-page__tag--runtime");

        Label versionTag = new Label("v" + gene.version());
        versionTag.getStyleClass().addAll("gep-page__tag", "gep-page__tag--runtime");

        tags.getChildren().addAll(categoryTag, runtimeTag, versionTag);

        if (!gene.enabled()) {
            Label disabledTag = new Label("禁用");
            disabledTag.getStyleClass().addAll("gep-page__tag", "gep-page__tag--disabled");
            tags.getChildren().add(disabledTag);
        } else {
            Label boostTag = new Label(String.format("boost %.2f", gene.epigeneticBoost()));
            boostTag.getStyleClass().addAll("gep-page__tag", "gep-page__tag--enabled");
            tags.getChildren().add(boostTag);
        }

        header.getChildren().add(tags);

        // Expand button
        Button expandBtn = new Button(expanded ? "收起" : "展开");
        expandBtn.getStyleClass().add("gep-page__btn");
        expandBtn.setOnAction(e -> {
            if (expandedGeneIds.contains(gene.id())) {
                expandedGeneIds.remove(gene.id());
            } else {
                expandedGeneIds.add(gene.id());
            }
            refreshContent();
        });
        header.getChildren().add(expandBtn);

        card.getChildren().add(header);

        // Expanded detail
        if (expanded) {
            card.getChildren().add(renderGeneDetail(gene));
        }

        return card;
    }

    private VBox renderGeneDetail(Gene gene) {
        VBox detail = new VBox(12);
        detail.getStyleClass().add("gep-page__gene-detail");

        // Strategy steps
        if (gene.strategy() != null && !gene.strategy().isEmpty()) {
            detail.getChildren().add(createGeneSection("策略步骤", gene.strategy(), false));
        }

        // Constraints
        if (gene.constraints() != null && !gene.constraints().isEmpty()) {
            List<String> constraintLines = gene.constraints().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .toList();
            detail.getChildren().add(createGeneSection("约束条件", constraintLines, false));
        }

        // Validation
        if (gene.validation() != null && !gene.validation().isEmpty()) {
            detail.getChildren().add(createGeneSection("验证检查", gene.validation(), true));
        }

        // Anti-patterns
        if (gene.antiPatterns() != null && !gene.antiPatterns().isEmpty()) {
            detail.getChildren().add(createGeneSection("反模式", gene.antiPatterns(), false));
        }

        // Code
        String code = gene.code();
        if (code != null && !code.isEmpty()) {
            VBox codeSection = new VBox(4);
            Label codeTitle = new Label("代码");
            codeTitle.getStyleClass().add("gep-page__gene-section-title");
            Label codeContent = new Label(code.length() > 500 ? code.substring(0, 500) + "..." : code);
            codeContent.getStyleClass().add("gep-page__gene-code");
            codeContent.setWrapText(true);
            codeSection.getChildren().addAll(codeTitle, codeContent);
            detail.getChildren().add(codeSection);
        }

        // Version history
        try {
            List<GeneRepository.CommitInfo> history = viewModel.getGeneHistory(gene.id());
            if (history != null && !history.isEmpty()) {
                VBox historySection = new VBox(4);
                Label historyTitle = new Label("版本历史");
                historyTitle.getStyleClass().add("gep-page__gene-section-title");
                for (GeneRepository.CommitInfo ci : history) {
                    HBox versionRow = new HBox(8);
                    versionRow.getStyleClass().add("gep-page__gene-version-row");
                    versionRow.setAlignment(Pos.CENTER_LEFT);

                    String time = LocalDateTime.ofInstant(Instant.ofEpochMilli(ci.timestamp() * 1000),
                            ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));
                    String hashShort = ci.hash().substring(0, Math.min(7, ci.hash().length()));
                    Label versionText = new Label(hashShort + "  " + ci.message());
                    versionText.getStyleClass().add("gep-page__gene-version-text");
                    versionText.setWrapText(true);

                    Region vSpacer = new Region();
                    HBox.setHgrow(vSpacer, Priority.ALWAYS);

                    Label timeLabel = new Label(time);
                    timeLabel.getStyleClass().add("gep-page__gene-version-time");

                    Button revertBtn = new Button("回滚");
                    revertBtn.getStyleClass().add("gep-page__btn");
                    String commitHash = ci.hash();
                    revertBtn.setOnAction(ev -> {
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setTitle("确认回滚");
                        alert.setHeaderText("回滚基因 " + gene.id() + " 到 " + commitHash);
                        alert.setContentText("确定要回滚吗？");
                        alert.showAndWait().ifPresent(resp -> {
                            if (resp == ButtonType.OK) {
                                viewModel.revertGene(gene.id(), commitHash);
                                refreshContent();
                            }
                        });
                    });

                    versionRow.getChildren().addAll(versionText, vSpacer, timeLabel, revertBtn);
                    historySection.getChildren().add(versionRow);
                }
                detail.getChildren().addAll(historyTitle, historySection);
            }
        } catch (Exception ignored) {}

        // Parent gene
        if (gene.parentId() != null && !gene.parentId().isEmpty()) {
            Label parentLabel = new Label("父基因: " + gene.parentId());
            parentLabel.getStyleClass().add("gep-page__gene-meta");
            detail.getChildren().add(parentLabel);
        }

        // Timestamps
        String created = formatTimestamp(gene.createdAt());
        String updated = formatTimestamp(gene.updatedAt());
        Label timeLabel = new Label("创建: " + created + "  更新: " + updated);
        timeLabel.getStyleClass().add("gep-page__gene-meta");
        detail.getChildren().add(timeLabel);

        // Action buttons
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button toggleBtn = new Button(gene.enabled() ? "禁用" : "启用");
        toggleBtn.getStyleClass().add("gep-page__btn");
        toggleBtn.setOnAction(e -> {
            viewModel.toggleGene(gene.id());
            refreshContent();
        });

        Button deleteBtn = new Button("删除");
        deleteBtn.getStyleClass().addAll("gep-page__btn", "gep-page__btn--danger");
        deleteBtn.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("确认删除");
            alert.setHeaderText("删除基因: " + gene.id());
            alert.setContentText("此操作不可恢复，确定要删除吗？");
            alert.showAndWait().ifPresent(resp -> {
                if (resp == ButtonType.OK) {
                    viewModel.deleteGene(gene.id());
                    refreshContent();
                }
            });
        });

        actions.getChildren().addAll(toggleBtn, deleteBtn);
        detail.getChildren().add(actions);

        return detail;
    }

    private VBox createGeneSection(String title, List<String> items, boolean checkmark) {
        VBox section = new VBox(2);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("gep-page__gene-section-title");
        section.getChildren().add(titleLabel);

        for (String item : items) {
            Label itemLabel = new Label((checkmark ? "✓ " : "") + item);
            itemLabel.getStyleClass().add("gep-page__gene-section-item");
            itemLabel.setWrapText(true);
            section.getChildren().add(itemLabel);
        }
        return section;
    }

    // ========== Routing Card ==========

    private VBox renderRoutingCard() {
        Label sectionLabel = new Label("路由");
        sectionLabel.getStyleClass().add("gep-page__section-label");

        VBox card = new VBox(8);
        card.getStyleClass().add("gep-page__card");

        List<RoutingEntry> routes = viewModel.getRoutes();
        if (routes.isEmpty()) {
            Label empty = new Label("暂无路由规则");
            empty.getStyleClass().add("gep-page__card-description");
            card.getChildren().add(empty);
        } else {
            for (RoutingEntry entry : routes) {
                card.getChildren().add(createRoutingRow(entry));
            }
        }

        Button addBtn = new Button("+ 添加路由");
        addBtn.getStyleClass().add("gep-page__btn");
        addBtn.setOnAction(e -> showAddRouteDialog());
        card.getChildren().add(addBtn);

        VBox wrapper = new VBox(12);
        wrapper.getChildren().addAll(sectionLabel, card);
        return wrapper;
    }

    private HBox createRoutingRow(RoutingEntry entry) {
        HBox row = new HBox(8);
        row.getStyleClass().add("gep-page__row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label patternLabel = new Label(entry.pattern());
        patternLabel.getStyleClass().add("gep-page__row-title");

        Label arrowLabel = new Label("→");
        arrowLabel.getStyleClass().add("gep-page__row-subtitle");

        Label geneLabel = new Label(entry.geneId());
        geneLabel.getStyleClass().add("gep-page__row-subtitle");

        Label weightLabel = new Label(String.format("(%.2f)", entry.weight()));
        weightLabel.getStyleClass().add("gep-page__row-subtitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button deleteBtn = new Button("删除");
        deleteBtn.getStyleClass().addAll("gep-page__btn", "gep-page__btn--danger");
        deleteBtn.setOnAction(e -> {
            viewModel.removeRoute(entry.pattern());
            refreshContent();
        });

        row.getChildren().addAll(patternLabel, arrowLabel, geneLabel, weightLabel, spacer, deleteBtn);
        return row;
    }

    // ========== Memory Card ==========

    private VBox renderMemoryCard() {
        Label sectionLabel = new Label("记忆");
        sectionLabel.getStyleClass().add("gep-page__section-label");

        VBox card = new VBox(8);
        card.getStyleClass().add("gep-page__card");

        List<MemoryRule> rules = viewModel.getRules();
        if (rules.isEmpty()) {
            Label empty = new Label("暂无记忆规则");
            empty.getStyleClass().add("gep-page__card-description");
            card.getChildren().add(empty);
        } else {
            for (MemoryRule rule : rules) {
                card.getChildren().add(createMemoryRow(rule));
            }
        }

        Button addBtn = new Button("+ 添加规则");
        addBtn.getStyleClass().add("gep-page__btn");
        addBtn.setOnAction(e -> showAddRuleDialog());
        card.getChildren().add(addBtn);

        VBox wrapper = new VBox(12);
        wrapper.getChildren().addAll(sectionLabel, card);
        return wrapper;
    }

    private HBox createMemoryRow(MemoryRule rule) {
        HBox row = new HBox(8);
        row.getStyleClass().add("gep-page__row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label patternLabel = new Label(rule.pattern());
        patternLabel.getStyleClass().add("gep-page__row-title");

        Label arrowLabel = new Label("→");
        arrowLabel.getStyleClass().add("gep-page__row-subtitle");

        Label actionLabel = new Label(rule.action());
        actionLabel.getStyleClass().add("gep-page__row-subtitle");

        Label confLabel = new Label(String.format("(%.0f%%)", rule.confidence() * 100));
        confLabel.getStyleClass().add("gep-page__row-subtitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button deleteBtn = new Button("删除");
        deleteBtn.getStyleClass().addAll("gep-page__btn", "gep-page__btn--danger");
        deleteBtn.setOnAction(e -> {
            viewModel.deleteRule(rule.id());
            refreshContent();
        });

        row.getChildren().addAll(patternLabel, arrowLabel, actionLabel, confLabel, spacer, deleteBtn);
        return row;
    }

    // ========== Events Card ==========

    private VBox renderEventsCard() {
        Label sectionLabel = new Label("进化事件");
        sectionLabel.getStyleClass().add("gep-page__section-label");

        VBox card = new VBox(8);
        card.getStyleClass().add("gep-page__card");

        List<EvolutionEvent> events = viewModel.getEvents();
        if (events.isEmpty()) {
            Label empty = new Label("暂无进化事件");
            empty.getStyleClass().add("gep-page__card-description");
            card.getChildren().add(empty);
        } else {
            List<EvolutionEvent> reversed = new ArrayList<>(events);
            Collections.reverse(reversed);
            int limit = Math.min(reversed.size(), 10);
            for (int i = 0; i < limit; i++) {
                card.getChildren().add(createEventRow(reversed.get(i)));
            }
        }

        VBox wrapper = new VBox(12);
        wrapper.getChildren().addAll(sectionLabel, card);
        return wrapper;
    }

    private HBox createEventRow(EvolutionEvent event) {
        HBox row = new HBox(8);
        row.getStyleClass().add("gep-page__row");
        row.setAlignment(Pos.CENTER_LEFT);

        String status = event.outcome() != null ? event.outcome().status() : "unknown";
        Label statusTag = new Label(status);
        statusTag.getStyleClass().addAll("gep-page__tag", getEventTagModifier(status));

        Label geneLabel = new Label(event.geneId() != null ? event.geneId() : "-");
        geneLabel.getStyleClass().add("gep-page__row-title");

        String detail = String.format("意图: %s | 分数: %.2f",
                event.intent() != null ? event.intent() : "-",
                event.outcome() != null ? event.outcome().score() : 0);
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("gep-page__row-subtitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String time = formatTimestamp(event.timestamp());
        Label timeLabel = new Label(time);
        timeLabel.getStyleClass().add("gep-page__row-subtitle");

        row.getChildren().addAll(statusTag, geneLabel, detailLabel, spacer, timeLabel);
        return row;
    }

    // ========== Capsules Card ==========

    private VBox renderCapsulesCard(List<Capsule> capsules) {
        Label sectionLabel = new Label("胶囊");
        sectionLabel.getStyleClass().add("gep-page__section-label");

        VBox card = new VBox(8);
        card.getStyleClass().add("gep-page__card");

        for (Capsule capsule : capsules) {
            HBox row = new HBox(8);
            row.getStyleClass().add("gep-page__row");
            row.setAlignment(Pos.CENTER_LEFT);

            Label idLabel = new Label(capsule.id());
            idLabel.getStyleClass().add("gep-page__row-title");

            Label scoreLabel = new Label(String.format("分数 %.2f", capsule.score()));
            scoreLabel.getStyleClass().add("gep-page__row-subtitle");

            Label genesLabel = new Label(String.join(", ", capsule.geneIds()));
            genesLabel.getStyleClass().add("gep-page__row-subtitle");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button deleteBtn = new Button("删除");
            deleteBtn.getStyleClass().addAll("gep-page__btn", "gep-page__btn--danger");
            deleteBtn.setOnAction(e -> {
                viewModel.deleteCapsule(capsule.id());
                refreshContent();
            });

            row.getChildren().addAll(idLabel, scoreLabel, genesLabel, spacer, deleteBtn);
            card.getChildren().add(row);
        }

        VBox wrapper = new VBox(12);
        wrapper.getChildren().addAll(sectionLabel, card);
        return wrapper;
    }

    // ========== Empty Card ==========

    private VBox createEmptyCard(String text) {
        VBox card = new VBox();
        card.getStyleClass().add("gep-page__card");
        Label empty = new Label(text);
        empty.getStyleClass().add("gep-page__empty");
        card.getChildren().add(empty);
        return card;
    }

    // ========== Dialogs ==========

    private void showAddRouteDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("添加路由");
        dialog.setHeaderText(null);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 10, 10, 10));

        TextField patternField = new TextField();
        patternField.setPromptText("匹配模式（正则）");
        TextField geneIdField = new TextField();
        geneIdField.setPromptText("目标基因ID");
        TextField weightField = new TextField("1.0");
        weightField.setPromptText("权重");

        grid.add(new Label("模式:"), 0, 0);
        grid.add(patternField, 1, 0);
        grid.add(new Label("基因ID:"), 0, 1);
        grid.add(geneIdField, 1, 1);
        grid.add(new Label("权重:"), 0, 2);
        grid.add(weightField, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    double weight = Double.parseDouble(weightField.getText().trim());
                    viewModel.addRoute(patternField.getText().trim(), geneIdField.getText().trim(), weight);
                    refreshContent();
                } catch (NumberFormatException ex) {
                    showError("输入错误", "权重必须是数字");
                }
            }
        });
    }

    private void showAddRuleDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("添加规则");
        dialog.setHeaderText(null);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 10, 10, 10));

        TextField patternField = new TextField();
        patternField.setPromptText("触发模式");
        TextField actionField = new TextField();
        actionField.setPromptText("执行动作");
        TextField confidenceField = new TextField("0.8");
        confidenceField.setPromptText("置信度 (0-1)");

        grid.add(new Label("模式:"), 0, 0);
        grid.add(patternField, 1, 0);
        grid.add(new Label("动作:"), 0, 1);
        grid.add(actionField, 1, 1);
        grid.add(new Label("置信度:"), 0, 2);
        grid.add(confidenceField, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    double confidence = Double.parseDouble(confidenceField.getText().trim());
                    viewModel.addRule(patternField.getText().trim(), actionField.getText().trim(), confidence);
                    refreshContent();
                } catch (NumberFormatException ex) {
                    showError("输入错误", "置信度必须是数字");
                }
            }
        });
    }

    // ========== Helpers ==========

    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) return "-";
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));
    }

    private String getEventTagModifier(String status) {
        if (status == null) return "gep-page__tag--unknown";
        return switch (status.toLowerCase()) {
            case "success" -> "gep-page__tag--success";
            case "pending" -> "gep-page__tag--pending";
            case "failed" -> "gep-page__tag--failed";
            default -> "gep-page__tag--unknown";
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
