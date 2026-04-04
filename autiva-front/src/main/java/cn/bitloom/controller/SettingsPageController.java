package cn.bitloom.controller;

import cn.bitloom.config.ConfigManager;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.store.Store;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettingsPageController implements Initializable, ButtonBarHolder, PageHolder {

    private final ConfigManager configManager;

    @FXML
    private VBox settingsPage;
    @FXML
    private TextField browserPathTextField;
    @FXML
    private Button browseBrowserButton;
    @FXML
    private TextField dingTalkClientIdField;
    @FXML
    private PasswordField dingTalkClientSecretField;
    @FXML
    private PasswordField deepseekApiKeyField;
    @FXML
    private PasswordField zApiKeyField;

    @Getter
    @Setter
    private IndexController indexController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        this.browseBrowserButton.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("选择浏览器可执行文件");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("可执行文件", "*.exe"));
            Path path = Paths.get(this.browserPathTextField.getText());
            if (Files.exists(path)) {
                fileChooser.setInitialDirectory(path.getParent().toFile());
            }
            File selectedFile = fileChooser.showOpenDialog(this.settingsPage.getScene().getWindow());
            if (Objects.nonNull(selectedFile)) {
                this.browserPathTextField.setText(selectedFile.getAbsolutePath());
            }
        });

        this.loadFromStore();
    }

    private void loadFromStore() {
        this.browserPathTextField.setText(this.configManager.getBrowserPath());
        this.dingTalkClientIdField.setText(this.configManager.getDingTalkClientId() != null ? this.configManager.getDingTalkClientId() : "");
        this.dingTalkClientSecretField.setText(this.configManager.getDingTalkClientSecret() != null ? this.configManager.getDingTalkClientSecret() : "");
        this.deepseekApiKeyField.setText(this.configManager.getDeepseekApiKey() != null ? this.configManager.getDeepseekApiKey() : "");
        this.zApiKeyField.setText(this.configManager.getZApiKey() != null ? this.configManager.getZApiKey() : "");
    }

    @Override
    public void show() {
        this.settingsPage.setVisible(true);
        this.settingsPage.setManaged(true);
        this.loadFromStore();
    }

    @Override
    public void hide() {
        this.settingsPage.setVisible(false);
        this.settingsPage.setManaged(false);
    }

    public void save() {
        configManager.setBrowserPath(browserPathTextField.getText());
        configManager.setDingTalkClientId(dingTalkClientIdField.getText());
        configManager.setDingTalkClientSecret(dingTalkClientSecretField.getText());
        configManager.setDeepseekApiKey(deepseekApiKeyField.getText());
        configManager.setZApiKey(zApiKeyField.getText());
        configManager.save();
        Store.statusText.set("配置已保存");
    }

    public void reset() {
        configManager.setBrowserPath("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
        configManager.setDingTalkClientId("");
        configManager.setDingTalkClientSecret("");
        configManager.setDeepseekApiKey("");
        configManager.setZApiKey("");
        configManager.save();
        this.loadFromStore();
        Store.statusText.set("重置成功");
    }

    @Override
    public List<ButtonBarHolder.ButtonConfig> getButtonConfigs() {
        return List.of(
                new ButtonBarHolder.ButtonConfig(
                        "saveSettingsButton",
                        "保存",
                        "dynamic-btn",
                        event -> this.save()
                ),
                new ButtonBarHolder.ButtonConfig(
                        "resetSettingsButton",
                        "重置",
                        "dynamic-btn",
                        event -> this.reset()
                )
        );
    }

}
