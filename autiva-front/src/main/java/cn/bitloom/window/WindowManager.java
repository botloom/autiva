package cn.bitloom.window;

import cn.bitloom.constant.AppConstants;
import cn.bitloom.holder.DialogHolder;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class WindowManager {

    private final ApplicationContext applicationContext;

    public <T> void showDialog(String fxmlPath, Window owner, Consumer<T> controllerInitializer) {
        try {
            FXMLLoader loader = new FXMLLoader(new ClassPathResource(fxmlPath).getURL());
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();
            T controller = loader.getController();

            double width = AppConstants.Stage.WIDTH;
            double height = AppConstants.Stage.HEIGHT;
            boolean resizable = false;
            StageStyle stageStyle = StageStyle.UNIFIED;

            if (controller instanceof DialogHolder holder) {
                width = holder.getWidth();
                height = holder.getHeight();
                resizable = holder.isResizable();
                stageStyle = holder.getStageStyle();
            }

            Scene scene = new Scene(root, width, height);
            Stage dialogStage = new Stage();
            dialogStage.initStyle(stageStyle);
            dialogStage.getIcons().add(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream(AppConstants.Stage.ICON))));

            if (resizable) {
                dialogStage.setResizable(true);
            }

            dialogStage.initModality(Modality.WINDOW_MODAL);

            if (owner != null) {
                dialogStage.initOwner(owner);
            }

            dialogStage.setScene(scene);

            if (controller instanceof StageAware stageAware) {
                stageAware.setStage(dialogStage);
            }

            if (controllerInitializer != null) {
                controllerInitializer.accept(controller);
            }

            dialogStage.showAndWait();
        } catch (Exception e) {
            log.error("Failed to open dialog: {}", fxmlPath, e);
            throw new WindowException("打开窗口失败: " + e.getMessage(), e);
        }
    }

    public <T> void showDialog(String fxmlPath, Window owner) {
        showDialog(fxmlPath, owner, null);
    }

    public interface StageAware {
        void setStage(Stage stage);
    }

    public static class WindowException extends RuntimeException {
        public WindowException(String message) {
            super(message);
        }

        public WindowException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
