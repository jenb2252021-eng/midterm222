package client_GUI;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public class AdminLoginDialog extends Stage {
    private boolean authenticated = false;
    private String username = null;

    private final String EARTH_CREAM = "#F5F7F5";  // Soft Sand

    public AdminLoginDialog(Window parent) {
        initOwner(parent);
        initModality(Modality.WINDOW_MODAL);
        setTitle("The Lorax - Admin Verification");
        setResizable(false);
        setWidth(400);
        setHeight(280);

        initUI();
    }

    public AdminLoginDialog(Stage parent) {
        this((Window) parent);
    }

    private void initUI() {
        BorderPane root = new BorderPane();
        // Soft Sand background for the whole dialog
        // Bark Brown
        String PANEL_DARK = "#294736";
        root.setStyle("-fx-background-color: " + EARTH_CREAM + "; -fx-border-color: " + PANEL_DARK + "; -fx-border-width: 2;");

        // Header - Bark Brown with White Text
        VBox headerBox = new VBox(5);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setStyle("-fx-background-color: " + PANEL_DARK + "; -fx-padding: 15;");


        boolean setup = JSONUtil.UserJSONUtil.needsAdministrator();
        Label lblTitle = new Label(setup ? "Create the local administrator" : "Administrator sign-in");
        lblTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: white; -fx-font-family: 'Segoe UI';");

        headerBox.getChildren().addAll(RegistryStyle.leaf(), lblTitle);
        root.setTop(headerBox);

        // Center Form
        GridPane center = new GridPane();
        center.setHgap(15);
        center.setVgap(12);
        center.setPadding(new Insets(20, 30, 20, 30));
        center.setAlignment(Pos.CENTER);

        TextField tfUser = new TextField("admin");
        // Sage Green
        String LEAF_LIGHT = "#A3B18A";
        tfUser.setStyle("-fx-background-radius: 5; -fx-border-color: " + LEAF_LIGHT + "; -fx-border-radius: 5;");

        PasswordField pf = new PasswordField();
        pf.setStyle("-fx-background-radius: 5; -fx-border-color: " + LEAF_LIGHT + "; -fx-border-radius: 5;");

        Label lblUser = new Label("Username:");
        lblUser.setStyle("-fx-text-fill: " + PANEL_DARK + "; -fx-font-weight: bold;");

        Label lblPass = new Label(setup ? "New password (8+ characters)" : "Password");
        lblPass.setStyle("-fx-text-fill: " + PANEL_DARK + "; -fx-font-weight: bold;");

        center.add(lblUser, 0, 0);
        center.add(tfUser, 1, 0);
        center.add(lblPass, 0, 1);
        center.add(pf, 1, 1);

        root.setCenter(center);

        // Footer Buttons
        Button btnCancel = new Button("Cancel");
        styleButton(btnCancel, "#8B0000"); // Earthy muted red

        Button btnLogin = new Button("Verify Access");
        // Earthy Palette Constants
        // Deep Forest Green
        String SDG_GREEN = "#2D5A27";
        styleButton(btnLogin, SDG_GREEN);

        btnLogin.setOnAction(_ -> {
            String user = tfUser.getText().trim();
            String pass = pf.getText();

            if (user.isEmpty() || pass.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Entry Denied", "Credentials required to access grove logs.");
                return;
            }

            if ("admin".equals(user) && (setup ? JSONUtil.UserJSONUtil.createLocalAdministrator(pass) : JSONUtil.UserJSONUtil.authenticate(user, pass))) {
                authenticated = true;
                username = user;
                close();
            } else {
                showAlert(Alert.AlertType.ERROR, "Verification Failed", "Unauthorized credentials.");
            }
        });

        btnCancel.setOnAction(_ -> {
            authenticated = false;
            username = null;
            close();
        });

        HBox footer = new HBox(15, btnCancel, btnLogin);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(0, 0, 20, 0));
        root.setBottom(footer);

        setScene(new Scene(root));
    }

    private void styleButton(Button btn, String color) {
        btn.setStyle("-fx-background-color: " + color + "; " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: bold; " +
                "-fx-background-radius: 10; " +
                "-fx-padding: 8 15; " +
                "-fx-cursor: hand;");
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.initOwner(this);

        // Basic styling for the alert dialog to match
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-background-color: " + EARTH_CREAM + ";");
        alert.showAndWait();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getUsername() {
        return username;
    }

    private static boolean authenticateAdminViaReflection(String username, String password) {
        try {
            Class<?> utilClass = Class.forName("JSONUtil.UserJSONUtil");
            Object ok = utilClass.getMethod("authenticate", String.class, String.class).invoke(null, username, password);
            return ok instanceof Boolean && (Boolean) ok;
        } catch (Exception e) {
            return false;
        }
    }
}
