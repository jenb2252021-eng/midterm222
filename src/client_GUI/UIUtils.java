package client_GUI;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;

public final class UIUtils {
    private UIUtils() {}

    public static void styleButton(Button btn, String bgHex) {
        btn.setStyle("-fx-background-color: " + bgHex + "; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 3; -fx-font-weight: bold;");
    }

    public static Image bytesToImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        return new Image(new ByteArrayInputStream(bytes));
    }

    /**
     * Informational results (successful saves, approvals, updates) appear as a non-blocking toast; warnings
     * and errors still open a dialog, because the user has to act on them.
     */
    public static void showAlert(Alert.AlertType type, String title, String message) {
        if (type == Alert.AlertType.INFORMATION) {
            Toast.success(message);
            return;
        }
        Alert alert = new Alert(type, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}

