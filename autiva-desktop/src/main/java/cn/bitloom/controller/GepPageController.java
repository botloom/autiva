package cn.bitloom.controller;

import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneType;
import cn.bitloom.agentic.evolve.repository.GeneRepository;
import cn.bitloom.agentic.evolve.solidify.EvolutionEvent;
import cn.bitloom.agentic.trace.ToolCallRecord;
import cn.bitloom.agentic.trace.Trace;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.vm.GepPageViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
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
        contentContainer.getChildren().add(renderOverviewCard());
        contentContainer.getChildren().add(renderClimbActionCard());
        contentContainer.getChildren().add(renderL2ReportCard());
        contentContainer.getChildren().add(renderRecentTracesCard());

        Label genesLabel = new Label("配置基因库");
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

        contentContainer.getChildren().add(renderEventsCard());
    }

    // ========== Overview Card ==========

    private VBox renderOverviewCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("gep-page__card");

        card.getChildren().add(createOverviewRow("基因总数", String.valueOf(viewModel.getGeneCount().get()),
                "启用 " + viewModel.getEnabledGeneCount().get()));
        card.getChildren().add(createOverviewRow("Prompt", String.valueOf(viewModel.getPromptGeneCount().get()), ""));
        card.getChildren().add(createOverviewRow("Rubric", String.valueOf(viewModel.getRubricGeneCount().get()), ""));
        card.getChildren().add(createOverviewRow("ToolDesc", String.valueOf(viewModel.getToolDescGeneCount().get()), ""));
        card.getChildren().add(createOverviewRow("SkillConfig", String.valueOf(viewModel.getSkillConfigGeneCount().get()), ""));
        card.getChildren().add(createOverviewRow("事件", String.valueOf(viewModel.getEventCount().get()),
                String.format("成功率 %.0f%%", viewModel.getSuccessRate().get() * 100)));

        return card;
    }

    private HBox createOverviewRow(String title, String value, String subtitle) {
        HBox row = new HBox(8);
        row.getStyleClass().add("gep-page__row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("gep-page__row-title");
        titleLabel.setPrefWidth(80);

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

    // ========== Climb Action Card ==========

    private VBox renderClimbActionCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("gep-page__card");

        Label sectionLabel = new Label("L4 爬山自优化");
        sectionLabel.getStyleClass().add("gep-page__card-title");

        Label descLabel = new Label("基于最近对话 Trace，由独立 analyzer Agent 分析高频缺陷，"
                + "对低效的 Prompt / Rubric / 工具描述 / 技能配置自动突变。高置信度建议自动应用，低置信度仅记录。");
        descLabel.getStyleClass().add("gep-page__card-description");
        descLabel.setWrapText(true);

        Button climbBtn = new Button("分析优化");
        climbBtn.getStyleClass().addAll("gep-page__btn", "gep-page__btn--primary");
        climbBtn.setOnAction(e -> {
            climbBtn.setDisable(true);
            climbBtn.setText("分析中...");
            viewModel.climbAsync(() -> {
                climbBtn.setDisable(false);
                climbBtn.setText("分析优化");
                refreshContent();
            });
        });

        String summary = viewModel.getLastClimbSummary().get();
        if (summary != null && !summary.isEmpty()) {
            Label summaryLabel = new Label(summary);
            summaryLabel.getStyleClass().add("gep-page__row-subtitle");
            summaryLabel.setWrapText(true);
            card.getChildren().addAll(sectionLabel, descLabel, climbBtn, summaryLabel);

            String analysis = viewModel.getLastClimbAnalysis().get();
            if (analysis != null && !analysis.isEmpty()) {
                card.getChildren().add(renderClimbAnalysisDetail(analysis));
            }
        } else {
            card.getChildren().addAll(sectionLabel, descLabel, climbBtn);
        }

        return card;
    }

    private TitledPane renderClimbAnalysisDetail(String analysis) {
        TitledPane pane = new TitledPane();
        pane.setText("L4 分析报告详情");
        pane.setExpanded(false);

        TextFlow flow = new TextFlow(new Text(analysis));
        flow.setPadding(new Insets(8, 4, 8, 4));
        pane.setContent(flow);
        return pane;
    }

    // ========== L2 Verification Report Card ==========

    private VBox renderL2ReportCard() {
        Label sectionLabel = new Label("L2 校验报告");
        sectionLabel.getStyleClass().add("gep-page__section-label");

        VBox card = new VBox(8);
        card.getStyleClass().add("gep-page__card");

        card.getChildren().add(createOverviewRow("Trace 总数",
                String.valueOf(viewModel.getTotalTraces().get()), ""));
        card.getChildren().add(createOverviewRow("通过",
                String.valueOf(viewModel.getPassedTraces().get()),
                String.format("通过率 %.1f%%", viewModel.getL2PassRate().get() * 100)));
        card.getChildren().add(createOverviewRow("失败",
                String.valueOf(viewModel.getFailedTraces().get()), ""));
        card.getChildren().add(createOverviewRow("工具调用",
                String.valueOf(viewModel.getTotalToolCalls().get()),
                "阻断 " + viewModel.getBlockedToolCalls().get()));

        VBox wrapper = new VBox(12);
        wrapper.getChildren().addAll(sectionLabel, card);
        return wrapper;
    }

    // ========== Recent Traces Card ==========

    private VBox renderRecentTracesCard() {
        Label sectionLabel = new Label("最近 Trace");
        sectionLabel.getStyleClass().add("gep-page__section-label");

        VBox card = new VBox(6);
        card.getStyleClass().add("gep-page__card");

        List<Trace> traces = viewModel.getRecentTraces();
        if (traces.isEmpty()) {
            Label empty = new Label("暂无 Trace 记录");
            empty.getStyleClass().add("gep-page__card-description");
            card.getChildren().add(empty);
        } else {
            List<Trace> reversed = new ArrayList<>(traces);
            Collections.reverse(reversed);
            int limit = Math.min(reversed.size(), 10);
            for (int i = 0; i < limit; i++) {
                card.getChildren().add(createTraceRow(reversed.get(i)));
            }
        }

        VBox wrapper = new VBox(12);
        wrapper.getChildren().addAll(sectionLabel, card);
        return wrapper;
    }

    private HBox createTraceRow(Trace trace) {
        HBox row = new HBox(8);
        row.getStyleClass().add("gep-page__row");
        row.setAlignment(Pos.CENTER_LEFT);

        String statusText = trace.verified() ? "通过" : "失败";
        Label statusTag = new Label(statusText);
        statusTag.getStyleClass().addAll("gep-page__tag",
                trace.verified() ? "gep-page__tag--success" : "gep-page__tag--failed");

        String time = formatTimestamp(trace.timestamp());
        Label timeLabel = new Label(time);
        timeLabel.getStyleClass().add("gep-page__row-subtitle");
        timeLabel.setPrefWidth(95);

        int toolCount = trace.toolCalls() != null ? trace.toolCalls().size() : 0;
        long blockedCount = trace.toolCalls() != null
                ? trace.toolCalls().stream().filter(ToolCallRecord::blocked).count() : 0;
        Label toolLabel = new Label("工具 " + toolCount + (blockedCount > 0 ? " (阻断 " + blockedCount + ")" : ""));
        toolLabel.getStyleClass().add("gep-page__row-subtitle");
        toolLabel.setPrefWidth(120);

        String userMsg = trace.userMessage() != null
                ? trace.userMessage().replace("\n", " ").trim() : "";
        if (userMsg.length() > 40) userMsg = userMsg.substring(0, 40) + "...";

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label msgLabel = new Label(userMsg);
        msgLabel.getStyleClass().add("gep-page__row-title");
        msgLabel.setWrapText(true);

        row.getChildren().addAll(statusTag, timeLabel, toolLabel, spacer, msgLabel);
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

        Label descLabel = new Label(gene.description() != null ? gene.description() : gene.name());
        descLabel.getStyleClass().add("gep-page__card-description");

        titleBox.getChildren().addAll(titleLabel, descLabel);
        header.getChildren().add(titleBox);

        // Tags
        HBox tags = new HBox(6);
        tags.setAlignment(Pos.CENTER_LEFT);

        Label typeTag = new Label(gene.type().name());
        typeTag.getStyleClass().addAll("gep-page__tag", "gep-page__tag--" + gene.type().name());

        Label targetTag = new Label(gene.targetId());
        targetTag.getStyleClass().addAll("gep-page__tag", "gep-page__tag--runtime");

        Label versionTag = new Label("v" + gene.version());
        versionTag.getStyleClass().addAll("gep-page__tag", "gep-page__tag--runtime");

        tags.getChildren().addAll(typeTag, targetTag, versionTag);

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

        // Config content
        if (gene.content() != null && !gene.content().isEmpty()) {
            VBox contentSection = new VBox(4);
            Label contentTitle = new Label("配置内容");
            contentTitle.getStyleClass().add("gep-page__gene-section-title");
            String contentText = gene.content().length() > 800
                    ? gene.content().substring(0, 800) + "..." : gene.content();
            Label contentLabel = new Label(contentText);
            contentLabel.getStyleClass().add("gep-page__gene-code");
            contentLabel.setWrapText(true);
            contentSection.getChildren().addAll(contentTitle, contentLabel);
            detail.getChildren().add(contentSection);
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
            Label parentLabel = new Label("父版本: " + gene.parentId());
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

    // ========== Empty Card ==========

    private VBox createEmptyCard(String text) {
        VBox card = new VBox();
        card.getStyleClass().add("gep-page__card");
        Label empty = new Label(text);
        empty.getStyleClass().add("gep-page__empty");
        card.getChildren().add(empty);
        return card;
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
