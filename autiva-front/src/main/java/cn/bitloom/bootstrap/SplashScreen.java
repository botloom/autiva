package cn.bitloom.bootstrap;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class SplashScreen {

    private static final double SPLASH_WIDTH = 200;
    private static final double SPLASH_HEIGHT = 200;

    private final javafx.scene.image.Image appIcon;

    public SplashScreen(javafx.scene.image.Image appIcon) {
        this.appIcon = appIcon;
    }

    public Stage show() {
        // 图标
        javafx.scene.image.ImageView iconView = new javafx.scene.image.ImageView();
        iconView.setImage(appIcon);
        iconView.setFitWidth(64);
        iconView.setFitHeight(64);
        iconView.setPreserveRatio(true);
        iconView.setSmooth(true);
        iconView.setScaleX(0.8);
        iconView.setScaleY(0.8);
        iconView.setOpacity(0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(400), iconView);
        scale.setFromX(0.8);
        scale.setFromY(0.8);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition fade = new FadeTransition(Duration.millis(400), iconView);
        fade.setFromValue(0);
        fade.setToValue(1);

        scale.play();
        fade.play();

        // 三点加载指示器
        HBox dotsContainer = new HBox(6);
        dotsContainer.setAlignment(Pos.CENTER);
        dotsContainer.setOpacity(0);

        Circle[] dots = new Circle[3];
        for (int i = 0; i < 3; i++) {
            dots[i] = new Circle(3, Color.valueOf("#0071e3"));
            dots[i].setOpacity(0.3);
            dotsContainer.getChildren().add(dots[i]);
        }

        // 点动画 - 依次闪烁
        Timeline dotsTimeline = new Timeline();
        for (int i = 0; i < 3; i++) {
            final int index = i;
            KeyFrame kf1 = new KeyFrame(Duration.millis(i * 200), e -> {
                dots[index].setOpacity(1.0);
            });
            KeyFrame kf2 = new KeyFrame(Duration.millis(i * 200 + 300), e -> {
                dots[index].setOpacity(0.3);
            });
            dotsTimeline.getKeyFrames().addAll(kf1, kf2);
        }
        dotsTimeline.getKeyFrames().add(new KeyFrame(Duration.millis(800)));
        dotsTimeline.setCycleCount(Timeline.INDEFINITE);

        FadeTransition dotsFade = new FadeTransition(Duration.millis(300), dotsContainer);
        dotsFade.setFromValue(0);
        dotsFade.setToValue(1);
        dotsFade.setDelay(Duration.millis(500));
        dotsFade.setOnFinished(e -> dotsTimeline.play());
        dotsFade.play();

        VBox content = new VBox(20, iconView, dotsContainer);
        content.setAlignment(Pos.CENTER);

        StackPane root = new StackPane(content);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.setStyle(
                "-fx-background-color: #ffffff;" +
                        "-fx-background-radius: 20;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 24, 0, 0, 4);"
        );

        Scene scene = new Scene(root, SPLASH_WIDTH, SPLASH_HEIGHT);
        scene.setFill(Color.TRANSPARENT);

        Stage stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.centerOnScreen();
        if (appIcon != null) {
            stage.getIcons().add(appIcon);
        }
        stage.show();

        return stage;
    }

    public void close() {
        // 无需清理
    }
}