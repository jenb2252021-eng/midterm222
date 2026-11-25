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

    public static void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}

