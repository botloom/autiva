package cn.bitloom.controller;

import cn.bitloom.agentic.skill.Skill;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.util.AlertUtil;
import cn.bitloom.vm.SkillPageViewModel;
import cn.bitloom.window.WindowManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillPageController implements Initializable, ButtonBarHolder, PageHolder {

    private final SkillPageViewModel viewModel;
    private final WindowManager windowManager;

    @FXML
    private VBox skillPage;
    @FXML
    private VBox skillListContainer;

    @Getter
    @Setter
    private IndexController indexController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        renderSkills();
    }

    private void renderSkills() {
        viewModel.loadSkills();
        skillListContainer.getChildren().clear();
        List<Skill> skills = viewModel.getSkills();

        if (skills.isEmpty()) {
            Label emptyLabel = new Label("暂无技能，点击上方\"导入\"按钮选择ZIP包导入技能");
            emptyLabel.getStyleClass().add("skill-page__empty");
            VBox.setMargin(emptyLabel, new Insets(40, 0, 0, 0));
            skillListContainer.getChildren().add(emptyLabel);
            return;
        }

        for (Skill skill : skills) {
            VBox card = createSkillCard(skill);
            skillListContainer.getChildren().add(card);
        }
    }

    private VBox createSkillCard(Skill skill) {
        VBox card = new VBox();
        card.getStyleClass().add("skill-page__card");
        card.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(card, new Insets(0, 0, 16, 0));

        HBox header = new HBox();
        header.getStyleClass().add("skill-page__card-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(12);

        Label nameLabel = new Label(skill.name());
        nameLabel.getStyleClass().add("skill-page__card-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button editButton = new Button("编辑");
        editButton.getStyleClass().add("skill-page__card-btn");
        editButton.setOnAction(event -> openFileEditor(skill));

        Button deleteButton = new Button("删除");
        deleteButton.getStyleClass().add("skill-page__card-btn");
        deleteButton.setStyle("-fx-text-fill: #ff3b30;");
        deleteButton.setOnAction(event -> {
            viewModel.deleteSkill(skill.name());
            renderSkills();
        });

        header.getChildren().addAll(nameLabel, spacer, editButton, deleteButton);

        String description = skill.description() != null ? skill.description() : "";
        Label descLabel = new Label(description);
        descLabel.getStyleClass().add("skill-page__card-description");
        descLabel.setWrapText(true);

        card.getChildren().addAll(header, descLabel);

        return card;
    }

    private void openFileEditor(Skill skill) {
        try {
            Path skillPath = Path.of(skill.basePath());
            windowManager.<FileEditorController>showDialog(
                    "cn/bitloom/view/FileEditorDialog.fxml",
                    skillPage.getScene().getWindow(),
                    controller -> controller.initRootPath(skillPath)
            );
        } catch (Exception e) {
            log.error("Failed to open file editor", e);
            AlertUtil.showError("打开编辑器失败", e.getMessage(), null);
        }
    }

    private void importSkillFromZip() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择技能ZIP包");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("ZIP Files", "*.zip")
        );

        File selectedFile = fileChooser.showOpenDialog(skillPage.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }

        try {
            Path zipPath = selectedFile.toPath();
            viewModel.importSkillFromZip(zipPath);
            renderSkills();
        } catch (IOException e) {
            log.error("Failed to import skill from zip", e);
            AlertUtil.showError("导入失败", e.getMessage(), null);
        }
    }

    @Override
    public void show() {
        this.skillPage.setVisible(true);
        this.skillPage.setManaged(true);
        renderSkills();
    }

    @Override
    public void hide() {
        this.skillPage.setVisible(false);
        this.skillPage.setManaged(false);
    }

    @Override
    public List<ButtonBarHolder.ButtonConfig> getButtonConfigs() {
        return List.of(
                new ButtonBarHolder.ButtonConfig(
                        "importSkillButton",
                        "导入",
                        "dynamic-btn",
                        event -> importSkillFromZip()
                )
        );
    }
}
