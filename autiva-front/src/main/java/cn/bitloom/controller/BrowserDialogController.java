package cn.bitloom.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BrowserDialogController {

    @FXML
    private Label statusLabel;
    @FXML
    private TextField urlField;
    @FXML
    private WebView browserWebView;

    @Getter
    @Setter
    private String teammateName;

    @Getter
    @Setter
    private Stage stage;

    private WebEngine webEngine;

    @FXML
    public void initialize() {
        this.webEngine = this.browserWebView.getEngine();
        
        this.webEngine.locationProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                javafx.application.Platform.runLater(() -> {
                    urlField.setText(newVal);
                    statusLabel.setText("加载完成: " + newVal);
                });
            }
        });

        this.webEngine.getLoadWorker().exceptionProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                log.error("Browser error", newVal);
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("加载失败");
                });
            }
        });
    }

    public void initData(String url) {
        this.urlField.setText(url);
        this.statusLabel.setText("正在加载: " + url);
        loadUrl(url);
    }

    @FXML
    private void goBack() {
        if (webEngine.getHistory() != null && webEngine.getHistory().getCurrentIndex() > 0) {
            webEngine.getHistory().go(-1);
        }
    }

    @FXML
    private void goForward() {
        if (webEngine.getHistory() != null) {
            int currentIndex = webEngine.getHistory().getCurrentIndex();
            int totalSize = webEngine.getHistory().getEntries().size();
            if (currentIndex < totalSize - 1) {
                webEngine.getHistory().go(1);
            }
        }
    }

    @FXML
    private void refresh() {
        webEngine.reload();
    }

    @FXML
    private void loadUrl() {
        String url = urlField.getText().trim();
        loadUrl(url);
    }

    private void loadUrl(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        try {
            statusLabel.setText("正在加载: " + url);
            webEngine.load(url);
        } catch (Exception e) {
            log.error("Failed to load URL: {}", url, e);
            statusLabel.setText("加载失败: " + url);
        }
    }

    public WebEngine getWebEngine() {
        return webEngine;
    }

    public String getCurrentUrl() {
        return webEngine.getLocation();
    }
}
