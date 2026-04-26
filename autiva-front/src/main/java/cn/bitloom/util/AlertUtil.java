package cn.bitloom.util;

import javafx.scene.control.Alert;
import javafx.stage.Window;

public final class AlertUtil {

    private AlertUtil() {
    }

    public static void showInfo(String message) {
        showInfo(null, message, null);
    }

    public static void showInfo(String title, String message, Window owner) {
        showAlert(Alert.AlertType.INFORMATION, title, message, owner);
    }

    public static void showWarning(String message) {
        showWarning(null, message, null);
    }

    public static void showWarning(String title, String message, Window owner) {
        showAlert(Alert.AlertType.WARNING, title, message, owner);
    }

    public static void showError(String message) {
        showError(null, message, null);
    }

    public static void showError(String title, String message, Window owner) {
        showAlert(Alert.AlertType.ERROR, title, message, owner);
    }

    public static boolean showConfirm(String message) {
        return showConfirm(null, message, null);
    }

    public static boolean showConfirm(String title, String message, Window owner) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setHeaderText(null);
        if (title != null) {
            alert.setTitle(title);
        }
        alert.setContentText(message);
        if (owner != null) {
            alert.initOwner(owner);
        }
        return alert.showAndWait().filter(response -> response == javafx.scene.control.ButtonType.OK).isPresent();
    }

    private static void showAlert(Alert.AlertType type, String title, String message, Window owner) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        if (title != null) {
            alert.setTitle(title);
        }
        alert.setContentText(message);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.showAndWait();
    }

}
