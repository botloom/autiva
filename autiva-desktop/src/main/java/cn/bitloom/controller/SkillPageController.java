package cn.bitloom.controller;

import cn.bitloom.agentic.skill.Skill;
import cn.bitloom.holder.DialogHolder;
import cn.bitloom.vm.SkillPageViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillPageController implements Initializable, DialogHolder {

    private final SkillPageViewModel viewModel;

    @FXML
    private VBox skillPage;
    @FXML
    private ListView<Skill> skillListView;

    @Override
    public double getWidth() {
        return 800;
    }

    @Override
    public double getHeight() {
        return 650;
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    /** 弹窗每次打开时刷新数据（showDialog initializer 调用） */
    public void refresh() {
        renderSkills();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        skillListView.setFocusTraversable(false);
        skillListView.setItems(viewModel.getSkills());
        skillListView.setCellFactory(list -> new SkillListCell());
        renderSkills();
    }

    private void renderSkills() {
        viewModel.loadSkillsAsync(null);
    }

    private VBox createSkillCard(Skill skill) {
        VBox card = new VBox();
        card.getStyleClass().add("skill-page__card");
        card.setMaxWidth(Double.MAX_VALUE);

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

    @FXML
    private void onImportSkill() {
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
        viewModel.importSkillFromZipAsync(zipPath, this::renderSkills);
    }

    /**
     * 技能列表 cell：渲染技能卡片，约束宽度避免水平滚动条
     */
    private class SkillListCell extends ListCell<Skill> {
        @Override
        protected void updateItem(Skill skill, boolean empty) {
            super.updateItem(skill, empty);
            if (empty || skill == null) {
                setGraphic(null);
            } else {
                VBox card = createSkillCard(skill);
                // 减去 ListView 左右 padding(32+32) + 垂直滚动条(6)
                card.prefWidthProperty().bind(
                        getListView().widthProperty().subtract(70)
                );
                card.maxWidthProperty().bind(
                        getListView().widthProperty().subtract(70)
                );
                setGraphic(card);
            }
        }
    }
}
