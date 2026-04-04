package cn.bitloom.controller;

import cn.bitloom.agentic.skill.Skill;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
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

    private final SkillManager skillManager;

    @FXML
    private VBox skillPage;
    @FXML
    private VBox skillListContainer;

    @Getter
    @Setter
    private IndexController indexController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadSkills();
    }

    private void loadSkills() {
        skillListContainer.getChildren().clear();
        List<Skill> skills = skillManager.getAllSkills();
        
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

        Label nameLabel = new Label(skill.getName());
        nameLabel.getStyleClass().add("skill-page__card-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button deleteButton = new Button("删除");
        deleteButton.getStyleClass().add("skill-page__card-btn");
        deleteButton.setOnAction(event -> deleteSkill(skill));

        header.getChildren().addAll(nameLabel, spacer, deleteButton);

        String description = skill.getDescription() != null ? skill.getDescription() : "";
        Label descLabel = new Label(description);
        descLabel.getStyleClass().add("skill-page__card-description");
        descLabel.setWrapText(true);

        card.getChildren().addAll(header, descLabel);

        return card;
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
            Skill importedSkill = skillManager.importSkillFromZip(zipPath);
            skillManager.loadSkills();
            loadSkills();
            
            showAlert(Alert.AlertType.INFORMATION, "导入成功", 
                    "技能 \"" + importedSkill.getName() + "\" 已成功导入");
        } catch (IOException e) {
            log.error("Failed to import skill from zip", e);
            showAlert(Alert.AlertType.ERROR, "导入失败", e.getMessage());
        }
    }

    private void deleteSkill(Skill skill) {
        skillManager.deleteSkill(skill.getName());
        skillManager.loadSkills();
        loadSkills();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initOwner(skillPage.getScene().getWindow());
        alert.showAndWait();
    }

    @Override
    public void show() {
        this.skillPage.setVisible(true);
        this.skillPage.setManaged(true);
        loadSkills();
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
