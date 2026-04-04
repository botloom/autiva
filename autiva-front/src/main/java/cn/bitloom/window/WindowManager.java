package cn.bitloom.window;

import cn.bitloom.constant.AppConstants;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Consumer;

@Slf4j
@Component
public class WindowManager {

    public <T> void showDialog(WindowConfig<T> config) {
        try {
            FXMLLoader loader = new FXMLLoader(new ClassPathResource(config.getFxmlPath()).getURL());
            Scene scene = new Scene(loader.load(), config.getWidth(), config.getHeight());

            Stage dialogStage = new Stage();
            dialogStage.initStyle(StageStyle.UNIFIED);
            dialogStage.getIcons().add(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream(AppConstants.Stage.ICON))));
            
            if (config.isResizable()) {
                dialogStage.setResizable(true);
            }
            
            dialogStage.initModality(Modality.WINDOW_MODAL);
            
            if (config.getOwner() != null) {
                dialogStage.initOwner(config.getOwner());
            }
            
            dialogStage.setScene(scene);
            
            if (config.getTitle() != null) {
                dialogStage.setTitle(config.getTitle());
            }

            T controller = loader.getController();
            
            if (controller instanceof StageAware stageAware) {
                stageAware.setStage(dialogStage);
            }
            
            if (config.getControllerInitializer() != null) {
                config.getControllerInitializer().accept(controller);
            }

            dialogStage.showAndWait();
        } catch (Exception e) {
            log.error("Failed to open dialog: {}", config.getFxmlPath(), e);
            throw new WindowException("打开窗口失败: " + e.getMessage(), e);
        }
    }

    public <T> WindowConfig.Builder<T> configBuilder() {
        return new WindowConfig.Builder<>();
    }

    @Getter
    public static class WindowConfig<T> {
        private final String fxmlPath;
        private final String title;
        private final Window owner;
        private final double width;
        private final double height;
        private final boolean resizable;
        private final Consumer<T> controllerInitializer;

        private WindowConfig(Builder<T> builder) {
            this.fxmlPath = builder.fxmlPath;
            this.title = builder.title;
            this.owner = builder.owner;
            this.width = builder.width;
            this.height = builder.height;
            this.resizable = builder.resizable;
            this.controllerInitializer = builder.controllerInitializer;
        }

        public static class Builder<T> {
            private String fxmlPath;
            private String title;
            private Window owner;
            private double width = AppConstants.Stage.WIDTH;
            private double height = AppConstants.Stage.HEIGHT;
            private boolean resizable = false;
            private Consumer<T> controllerInitializer;

            public Builder<T> fxmlPath(String fxmlPath) {
                this.fxmlPath = fxmlPath;
                return this;
            }

            public Builder<T> title(String title) {
                this.title = title;
                return this;
            }

            public Builder<T> owner(Window owner) {
                this.owner = owner;
                return this;
            }

            public Builder<T> width(double width) {
                this.width = width;
                return this;
            }

            public Builder<T> height(double height) {
                this.height = height;
                return this;
            }

            public Builder<T> resizable(boolean resizable) {
                this.resizable = resizable;
                return this;
            }

            public Builder<T> controllerInitializer(Consumer<T> initializer) {
                this.controllerInitializer = initializer;
                return this;
            }

            public WindowConfig<T> build() {
                Objects.requireNonNull(fxmlPath, "FXML path must not be null");
                return new WindowConfig<>(this);
            }
        }
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
