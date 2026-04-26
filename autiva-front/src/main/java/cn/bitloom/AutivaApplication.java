package cn.bitloom;

import cn.bitloom.constant.AppConstants;
import cn.bitloom.util.ExecutorManager;
import cn.bitloom.window.WindowChromeHelper;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.util.Objects;

@Slf4j
@EnableScheduling
@SpringBootApplication
public class AutivaApplication extends Application {

    private ConfigurableApplicationContext springContext;

    public static void main(String[] args) {
        Application.launch(args);
    }

    @Override
    public void init() {
        springContext = SpringApplication.run(AutivaApplication.class);
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.createAppDirsIfNotExist();
        FXMLLoader loader = new FXMLLoader(getClass().getResource(AppConstants.Stage.FXML));
        loader.setControllerFactory(springContext::getBean);
        Scene scene = new Scene(loader.load(), AppConstants.Stage.WIDTH, AppConstants.Stage.HEIGHT);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.getIcons().add(new Image(Objects.requireNonNull(this.getClass().getResourceAsStream(AppConstants.Stage.ICON))));

        HBox toolbar = (HBox) scene.lookup("#toolbar");
        Region rootContainer = (Region) scene.lookup("#rootContainer");
        Button minimizeBtn = (Button) scene.lookup("#minimizeBtn");
        Button maximizeBtn = (Button) scene.lookup("#maximizeBtn");
        Button closeBtn = (Button) scene.lookup("#closeBtn");

        WindowChromeHelper.setup(stage, toolbar, rootContainer, minimizeBtn, maximizeBtn, closeBtn, 600, 400);

        stage.show();
    }

    @Override
    public void stop() {
        ExecutorManager.close();
        springContext.close();
    }

    private void createAppDirsIfNotExist() {
        try {
            if (!Files.exists(AppConstants.Base.APP_DIR)) {
                Files.createDirectories(AppConstants.Base.APP_DIR);
            }
            if (!Files.exists(AppConstants.Base.LOGS_DIR)) {
                Files.createDirectories(AppConstants.Base.LOGS_DIR);
            }
            if (!Files.exists(AppConstants.Base.SKILL_DIR)) {
                Files.createDirectories(AppConstants.Base.SKILL_DIR);
            }
            if (!Files.exists(AppConstants.Base.MCP_DIR)) {
                Files.createDirectories(AppConstants.Base.MCP_DIR);
            }
            if (!Files.exists(AppConstants.Base.WORKSPACE_DIR)) {
                Files.createDirectories(AppConstants.Base.WORKSPACE_DIR);
            }
            if (!Files.exists(AppConstants.Base.MCP_CONFIG_FILE)) {
                Files.createFile(AppConstants.Base.MCP_CONFIG_FILE);
            }
        } catch (Exception e) {
            log.error("创建应用目录失败", e);
        }
    }
}
