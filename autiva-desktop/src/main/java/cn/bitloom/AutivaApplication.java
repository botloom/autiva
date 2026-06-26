package cn.bitloom;

import cn.bitloom.bootstrap.AppBootstrap;
import cn.bitloom.bootstrap.SplashScreen;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.node.svg.SvgImageView;
import cn.bitloom.util.ExecutorManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

@Slf4j
@SpringBootApplication
public class AutivaApplication extends Application {

    private ConfigurableApplicationContext springContext;
    private TrayIcon trayIcon;
    private Popup trayPopup;
    private Stage hiddenOwner;
    private double savedX, savedY, savedWidth, savedHeight;

    static void main(String[] args) {
        Application.launch(args);
    }

    @Override
    public void init() {
    }

    @Override
    public void start(Stage stage) {
        Toolkit.getDefaultToolkit();

        SvgImageView iconView = new SvgImageView(AppConstants.Stage.ICON_SVG);
        iconView.setFitWidth(100);
        iconView.setFitHeight(100);
        SplashScreen splash = new SplashScreen(iconView);
        Stage splashStage = splash.show();

        Thread loadingThread = new Thread(() -> {
            try {
                AppBootstrap.initialize();
                springContext = SpringApplication.run(AutivaApplication.class);
            } catch (Exception e) {
                log.error("启动失败", e);
                Platform.exit();
                return;
            }

            Platform.runLater(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource(AppConstants.Stage.FXML));
                    loader.setControllerFactory(springContext::getBean);
                    Scene mainScene = new Scene(loader.load(), AppConstants.Stage.WIDTH, AppConstants.Stage.HEIGHT);

                    splash.close();
                    splashStage.close();

                    stage.initStyle(StageStyle.UNIFIED);
                    stage.setScene(mainScene);
                    stage.getIcons().add(iconView.getImage());
                    stage.setMinWidth(1200);
                    stage.setMinHeight(700);

                    setupTrayIcon(stage, iconView.getImage());

                    stage.setOnCloseRequest(e -> {
                        e.consume();
                        savedX = stage.getX();
                        savedY = stage.getY();
                        savedWidth = stage.getWidth();
                        savedHeight = stage.getHeight();
                        stage.hide();
                    });

                    stage.show();
                } catch (Exception e) {
                    log.error("主界面加载失败", e);
                }
            });
        }, "app-bootstrap");
        loadingThread.setDaemon(true);
        loadingThread.start();
    }

    @Override
    public void stop() {
        removeTrayIcon();
        ExecutorManager.close();
        if (springContext != null) {
            springContext.close();
        }
        System.exit(0);
    }

    private void restoreMainWindow(Stage mainStage) {
        mainStage.setX(savedX);
        mainStage.setY(savedY);
        mainStage.setWidth(savedWidth > 0 ? savedWidth : AppConstants.Stage.WIDTH);
        mainStage.setHeight(savedHeight > 0 ? savedHeight : AppConstants.Stage.HEIGHT);
        mainStage.show();
        mainStage.toFront();
    }

    private void setupTrayIcon(Stage mainStage, Image appIcon) {
        if (!SystemTray.isSupported()) {
            log.warn("系统不支持托盘图标");
            return;
        }

        hiddenOwner = new Stage();
        hiddenOwner.initStyle(StageStyle.UTILITY);
        hiddenOwner.setWidth(1);
        hiddenOwner.setHeight(1);
        hiddenOwner.setOpacity(0);
        hiddenOwner.show();
        hiddenOwner.toBack();

        SystemTray tray = SystemTray.getSystemTray();

        int iconSize = tray.getTrayIconSize().width;
        if (iconSize <= 0) iconSize = 16;
        java.awt.Image awtImage = SwingFXUtils.fromFXImage(appIcon, null);
        java.awt.Image scaledImage = awtImage.getScaledInstance(iconSize, iconSize, java.awt.Image.SCALE_SMOOTH);

        trayIcon = new TrayIcon(scaledImage, "Autiva");
        trayIcon.setImageAutoSize(true);

        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() >= 2) {
                    Platform.runLater(() -> restoreMainWindow(mainStage));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    Platform.runLater(() -> showTrayContextMenu(e.getX(), e.getY()));
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    Platform.runLater(() -> showTrayContextMenu(e.getX(), e.getY()));
                }
            }
        });

        try {
            tray.add(trayIcon);
            log.info("系统托盘图标已添加");
        } catch (AWTException e) {
            log.warn("添加系统托盘图标失败", e);
        }

        buildTrayContextMenu(mainStage);
    }

    private void buildTrayContextMenu(Stage mainStage) {
        trayPopup = new Popup();
        trayPopup.setAutoHide(true);
        trayPopup.setAutoFix(true);

        VBox menuBox = new VBox(2);
        menuBox.setPadding(new Insets(6, 4, 6, 4));
        menuBox.setStyle("""
                -fx-background-color: #ffffff;
                -fx-background-radius: 12px;
                -fx-border-color: rgba(0, 0, 0, 0.1);
                -fx-border-width: 1px;
                -fx-border-radius: 12px;
                -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.12), 16, 0.2, 0, 4);
                """);

        Label showLabel = createMenuLabel("显示主窗口");
        showLabel.setOnMouseClicked(e -> {
            trayPopup.hide();
            restoreMainWindow(mainStage);
        });

        Separator separator = new Separator();
        separator.setPadding(new Insets(4, 8, 4, 8));

        Label exitLabel = createMenuLabel("退出 Autiva");
        exitLabel.setOnMouseClicked(e -> {
            trayPopup.hide();
            removeTrayIcon();
            Platform.exit();
        });

        menuBox.getChildren().addAll(showLabel, separator, exitLabel);
        trayPopup.getContent().add(menuBox);
    }

    private Label createMenuLabel(String text) {
        Label label = new Label(text);
        label.setPadding(new Insets(8, 16, 8, 12));
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle("""
                -fx-font-family: "SF Pro Text", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                -fx-font-size: 13px;
                -fx-text-fill: #333333;
                -fx-background-color: transparent;
                -fx-background-radius: 8px;
                -fx-cursor: hand;
                """);
        label.setOnMouseEntered(e -> label.setStyle("""
                -fx-font-family: "SF Pro Text", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                -fx-font-size: 13px;
                -fx-text-fill: #333333;
                -fx-background-color: #f5f5f7;
                -fx-background-radius: 8px;
                -fx-cursor: hand;
                """));
        label.setOnMouseExited(e -> label.setStyle("""
                -fx-font-family: "SF Pro Text", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                -fx-font-size: 13px;
                -fx-text-fill: #333333;
                -fx-background-color: transparent;
                -fx-background-radius: 8px;
                -fx-cursor: hand;
                """));
        return label;
    }

    private void showTrayContextMenu(int screenX, int screenY) {
        if (trayPopup == null) return;

        if (trayPopup.isShowing()) {
            trayPopup.hide();
        }
        trayPopup.show(hiddenOwner, screenX, screenY);
    }

    private void removeTrayIcon() {
        if (trayPopup != null) {
            trayPopup.hide();
            trayPopup = null;
        }
        if (trayIcon != null) {
            try {
                SystemTray.getSystemTray().remove(trayIcon);
            } catch (Exception e) {
                // ignore
            }
            trayIcon = null;
        }
    }
}
