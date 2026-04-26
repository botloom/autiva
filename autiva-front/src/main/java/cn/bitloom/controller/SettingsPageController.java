package cn.bitloom.controller;

import cn.bitloom.bridge.weixin.WeixinILinkClient;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.vm.SettingsPageViewModel;
import cn.bitloom.window.WindowManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
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

    private final SettingsPageViewModel viewModel;
    private final ApplicationContext applicationContext;

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
    private CheckBox weixinEnabledCheckBox;
    @FXML
    private Label weixinStatusLabel;
    @FXML
    private Button weixinLoginButton;
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

        this.weixinLoginButton.setOnAction(event -> openWeixinLoginDialog());

        this.bindViewModel();
    }

    private void openWeixinLoginDialog() {
        try {
            WeixinILinkClient client = applicationContext.getBean(WeixinILinkClient.class);
            WindowManager windowManager = applicationContext.getBean(WindowManager.class);
            windowManager.showDialog(
                    "cn/bitloom/view/WeixinLoginDialog.fxml",
                    settingsPage.getScene().getWindow(),
                    controller -> {
                        WeixinLoginController loginController = (WeixinLoginController) controller;
                        loginController.setWeixinILinkClient(client);
                    }
            );
        } catch (Exception e) {
            log.warn("微信 iLink 未启用，请先在配置中启用", e);
        }
    }

    private void bindViewModel() {
        browserPathTextField.textProperty().bindBidirectional(viewModel.getBrowserPath());
        dingTalkClientIdField.textProperty().bindBidirectional(viewModel.getDingTalkClientId());
        dingTalkClientSecretField.textProperty().bindBidirectional(viewModel.getDingTalkClientSecret());
        weixinEnabledCheckBox.selectedProperty().bindBidirectional(viewModel.getWeixinEnabled());
        deepseekApiKeyField.textProperty().bindBidirectional(viewModel.getDeepseekApiKey());
        zApiKeyField.textProperty().bindBidirectional(viewModel.getZApiKey());
    }

    @Override
    public void show() {
        this.settingsPage.setVisible(true);
        this.settingsPage.setManaged(true);
        viewModel.loadFromStore();
        updateWeixinStatus();
    }

    @Override
    public void hide() {
        this.settingsPage.setVisible(false);
        this.settingsPage.setManaged(false);
    }

    private void updateWeixinStatus() {
        try {
            WeixinILinkClient client = applicationContext.getBean(WeixinILinkClient.class);
            if (client.isConnected()) {
                weixinStatusLabel.setText("已连接");
                weixinStatusLabel.setStyle("-fx-text-fill: #34c759;");
            } else {
                weixinStatusLabel.setText("未连接");
                weixinStatusLabel.setStyle("-fx-text-fill: #86868b;");
            }
            client.connectedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    weixinStatusLabel.setText("已连接");
                    weixinStatusLabel.setStyle("-fx-text-fill: #34c759;");
                } else {
                    weixinStatusLabel.setText("未连接");
                    weixinStatusLabel.setStyle("-fx-text-fill: #86868b;");
                }
            });
        } catch (Exception e) {
            weixinStatusLabel.setText("未启用");
            weixinStatusLabel.setStyle("-fx-text-fill: #86868b;");
        }
    }

    @Override
    public List<ButtonBarHolder.ButtonConfig> getButtonConfigs() {
        return List.of(
                new ButtonBarHolder.ButtonConfig(
                        "saveSettingsButton",
                        "保存",
                        "dynamic-btn",
                        event -> viewModel.save()
                ),
                new ButtonBarHolder.ButtonConfig(
                        "resetSettingsButton",
                        "重置",
                        "dynamic-btn",
                        event -> viewModel.reset()
                )
        );
    }

}
