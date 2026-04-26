package cn.bitloom.window;

import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.HashSet;
import java.util.Set;

public final class WindowChromeHelper {

    private static final String MAXIMIZED_STYLE = "window-chrome--maximized";
    private static final int RESIZE_MARGIN = 8;
    private static final double DEFAULT_CLIP_ARC = 12;
    private static final Set<Stage> resizeSetupStages = new HashSet<>();

    private WindowChromeHelper() {
    }

    public static void setup(Stage stage, Node dragNode, Region clipTarget,
                             Button minimizeBtn, Button maximizeBtn, Button closeBtn,
                             double minWidth, double minHeight) {
        if (stage == null) return;

        stage.setMinWidth(minWidth);
        stage.setMinHeight(minHeight);

        Platform.runLater(() -> {
            setupWindowControls(stage, clipTarget, minimizeBtn, maximizeBtn, closeBtn);
            setupDrag(stage, dragNode);
            setupResize(stage);
            if (clipTarget != null) {
                setupClip(clipTarget, DEFAULT_CLIP_ARC);
            }
        });
    }

    private static void setupWindowControls(Stage stage, Region clipTarget,
                                            Button minimizeBtn, Button maximizeBtn, Button closeBtn) {
        if (minimizeBtn != null) {
            minimizeBtn.setGraphic(createMinimizeIcon());
            minimizeBtn.setOnAction(e -> stage.setIconified(true));
        }
        if (maximizeBtn != null) {
            maximizeBtn.setGraphic(createMaximizeIcon());
            maximizeBtn.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
            stage.maximizedProperty().addListener((obs, oldVal, newVal) -> {
                Region chromeRoot = (Region) stage.getScene().getRoot();
                if (newVal) {
                    maximizeBtn.getStyleClass().remove("window-chrome__btn--maximize");
                    maximizeBtn.getStyleClass().add("window-chrome__btn--restore");
                    maximizeBtn.setGraphic(createRestoreIcon());
                    chromeRoot.getStyleClass().add(MAXIMIZED_STYLE);
                    if (clipTarget != null) clipTarget.setClip(null);
                } else {
                    maximizeBtn.getStyleClass().remove("window-chrome__btn--restore");
                    maximizeBtn.getStyleClass().add("window-chrome__btn--maximize");
                    maximizeBtn.setGraphic(createMaximizeIcon());
                    chromeRoot.getStyleClass().remove(MAXIMIZED_STYLE);
                    if (clipTarget != null) setupClip(clipTarget, DEFAULT_CLIP_ARC);
                }
            });
        }
        if (closeBtn != null) {
            closeBtn.setGraphic(createCloseIcon());
            closeBtn.setOnAction(e -> {
                if (stage.getOnCloseRequest() != null) {
                    stage.getOnCloseRequest().handle(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
                }
                stage.close();
            });
        }
    }

    public static void setupDrag(Stage stage, Node dragNode) {
        if (stage == null || dragNode == null) return;

        final double[] dragStartX = {0};
        final double[] dragStartY = {0};
        final double[] restoredW = {0};
        final double[] restoredH = {0};

        dragNode.setOnMousePressed(e -> {
            if (isButtonTarget(e)) return;
            dragStartX[0] = e.getScreenX() - stage.getX();
            dragStartY[0] = e.getScreenY() - stage.getY();
        });

        dragNode.setOnMouseDragged(e -> {
            if (isButtonTarget(e)) return;
            if (stage.isMaximized()) {
                restoredW[0] = stage.getWidth();
                restoredH[0] = stage.getHeight();
                stage.setMaximized(false);
                double ratio = dragStartX[0] / restoredW[0];
                stage.setWidth(restoredW[0]);
                stage.setHeight(restoredH[0]);
                dragStartX[0] = ratio * stage.getWidth();
            }
            stage.setX(e.getScreenX() - dragStartX[0]);
            stage.setY(e.getScreenY() - dragStartY[0]);
        });

        dragNode.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && !isButtonTarget(e)) {
                stage.setMaximized(!stage.isMaximized());
            }
        });
    }

    public static void setupResize(Stage stage) {
        if (stage == null || resizeSetupStages.contains(stage)) return;
        Scene scene = stage.getScene();
        if (scene == null) return;

        resizeSetupStages.add(stage);
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> resizeSetupStages.remove(stage));

        final double[] startDragX = {0};
        final double[] startDragY = {0};
        final double[] startWidth = {0};
        final double[] startHeight = {0};
        final double[] startStageX = {0};
        final double[] startStageY = {0};
        final int[] direction = {0};

        Region root = (Region) scene.getRoot();

        root.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            if (stage.isMaximized()) {
                root.setCursor(Cursor.DEFAULT);
                return;
            }
            root.setCursor(computeResizeCursor(event.getX(), event.getY(),
                    root.getWidth(), root.getHeight(), RESIZE_MARGIN));
        });

        root.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (stage.isMaximized()) return;
            int dir = computeResizeDirection(event.getX(), event.getY(),
                    root.getWidth(), root.getHeight(), RESIZE_MARGIN);
            if (dir != 0) {
                startDragX[0] = event.getScreenX();
                startDragY[0] = event.getScreenY();
                startWidth[0] = stage.getWidth();
                startHeight[0] = stage.getHeight();
                startStageX[0] = stage.getX();
                startStageY[0] = stage.getY();
                direction[0] = dir;
                event.consume();
            }
        });

        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (direction[0] == 0) return;

            double dx = event.getScreenX() - startDragX[0];
            double dy = event.getScreenY() - startDragY[0];

            double newW = startWidth[0];
            double newH = startHeight[0];
            double newX = startStageX[0];
            double newY = startStageY[0];

            if ((direction[0] & 1) != 0) { newH = startHeight[0] - dy; newY = startStageY[0] + dy; }
            if ((direction[0] & 2) != 0) { newH = startHeight[0] + dy; }
            if ((direction[0] & 4) != 0) { newW = startWidth[0] - dx; newX = startStageX[0] + dx; }
            if ((direction[0] & 8) != 0) { newW = startWidth[0] + dx; }

            if (newW >= stage.getMinWidth()) {
                stage.setWidth(newW);
                if ((direction[0] & 4) != 0) stage.setX(newX);
            }
            if (newH >= stage.getMinHeight()) {
                stage.setHeight(newH);
                if ((direction[0] & 1) != 0) stage.setY(newY);
            }
            event.consume();
        });

        root.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> direction[0] = 0);
    }

    public static void setupClip(Region target, double arcSize) {
        if (target == null) return;
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(target.widthProperty());
        clip.heightProperty().bind(target.heightProperty());
        clip.setArcWidth(arcSize);
        clip.setArcHeight(arcSize);
        target.setClip(clip);
    }

    private static Node createMinimizeIcon() {
        javafx.scene.shape.Rectangle line = new javafx.scene.shape.Rectangle(10, 1.5);
        line.setFill(Color.rgb(29, 29, 31));
        line.setTranslateY(4);
        return new javafx.scene.Group(line);
    }

    private static Node createMaximizeIcon() {
        javafx.scene.shape.Rectangle rect = new javafx.scene.shape.Rectangle(10, 10);
        rect.setFill(Color.TRANSPARENT);
        rect.setStroke(Color.rgb(29, 29, 31));
        rect.setStrokeWidth(1.5);
        return new javafx.scene.Group(rect);
    }

    private static Node createRestoreIcon() {
        javafx.scene.shape.Rectangle back = new javafx.scene.shape.Rectangle(8, 8);
        back.setFill(Color.TRANSPARENT);
        back.setStroke(Color.rgb(29, 29, 31));
        back.setStrokeWidth(1.5);
        back.setTranslateX(3);
        back.setTranslateY(-3);

        javafx.scene.shape.Rectangle front = new javafx.scene.shape.Rectangle(8, 8);
        front.setFill(Color.rgb(255, 255, 255));
        front.setStroke(Color.rgb(29, 29, 31));
        front.setStrokeWidth(1.5);
        front.setTranslateX(-1);
        front.setTranslateY(1);

        return new javafx.scene.Group(back, front);
    }

    private static Node createCloseIcon() {
        javafx.scene.shape.Line l1 = new javafx.scene.shape.Line(0, 0, 10, 10);
        l1.setStroke(Color.rgb(29, 29, 31));
        l1.setStrokeWidth(1.5);
        javafx.scene.shape.Line l2 = new javafx.scene.shape.Line(10, 0, 0, 10);
        l2.setStroke(Color.rgb(29, 29, 31));
        l2.setStrokeWidth(1.5);
        return new javafx.scene.Group(l1, l2);
    }

    private static boolean isButtonTarget(MouseEvent e) {
        Node target = (Node) e.getTarget();
        while (target != null) {
            if (target instanceof Button) return true;
            target = target.getParent();
        }
        return false;
    }

    private static Cursor computeResizeCursor(double x, double y, double w, double h, int margin) {
        boolean top = y < margin;
        boolean bottom = y > h - margin;
        boolean left = x < margin;
        boolean right = x > w - margin;

        if (top && left) return Cursor.NW_RESIZE;
        if (top && right) return Cursor.NE_RESIZE;
        if (bottom && left) return Cursor.SW_RESIZE;
        if (bottom && right) return Cursor.SE_RESIZE;
        if (top) return Cursor.N_RESIZE;
        if (bottom) return Cursor.S_RESIZE;
        if (left) return Cursor.W_RESIZE;
        if (right) return Cursor.E_RESIZE;
        return Cursor.DEFAULT;
    }

    private static int computeResizeDirection(double x, double y, double w, double h, int margin) {
        int dir = 0;
        if (y < margin) dir |= 1;
        if (y > h - margin) dir |= 2;
        if (x < margin) dir |= 4;
        if (x > w - margin) dir |= 8;
        return dir;
    }
}
