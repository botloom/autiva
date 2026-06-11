package cn.bitloom.controller;

import cn.bitloom.agentic.skill.Skill;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.vm.SkillPageViewModel;
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
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillPageController implements Initializable, ButtonBarHolder, PageHolder {

    private final SkillPageViewModel viewModel;

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
        skillListContainer.getChildren().clear();
        viewModel.loadSkillsAsync(() -> {
            List<Skill> skills = viewModel.getSkills();
            for (Skill skill : skills) {
                VBox card = createSkillCard(skill);
                skillListContainer.getChildren().add(card);
            }
        });
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

        Button deleteButton = new Button("删除");
        deleteButton.getStyleClass().add("skill-page__card-btn");
        deleteButton.setStyle("-fx-text-fill: #ff3b30;");
        deleteButton.setOnAction(event -> {
            viewModel.deleteSkill(skill.name());
            renderSkills();
        });

        header.getChildren().addAll(nameLabel, spacer, deleteButton);

        String description = skill.description() != null ? skill.description() : "";
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

        Path zipPath = selectedFile.toPath();
        viewModel.importSkillFromZipAsync(zipPath, () -> {
            renderSkills();
        });
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
