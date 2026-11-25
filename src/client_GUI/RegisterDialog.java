package client_GUI;

import Server.UserService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public class RegisterDialog extends Stage {
    // --- Advanced Botanical Palette ---
    private final String COLOR_BG      = "#F1F2DA"; // Light Moss Base
    private final String COLOR_WOOD    = "#20865b"; // Bark Brown
    private final String COLOR_LEAF    = "#4F772D"; // Forest Green

    private final UserService userService;
    private final TextField tfUser = new TextField();
    private final PasswordField pfPass = new PasswordField();
    private final PasswordField pfConfirm = new PasswordField();
    private final TextField tfPassVisible = new TextField();
    private final TextField tfConfirmVisible = new TextField();

    private final Label lblError = new Label(" ");
    private final Label lblStrengthValue = new Label("None");

    public RegisterDialog(Window parent, UserService userService) {
        this.userService = userService;
        initOwner(parent);
        initModality(Modality.WINDOW_MODAL);
        setTitle("The Lorax - Ranger Recruitment");
        setResizable(false);
        setWidth(480);
        setHeight(700);

        initUI();
    }

    private void initUI() {
        VBox root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("login-background");
        VBox card = new VBox(10);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(390);
        card.getChildren().addAll(RegistryStyle.leaf(), RegistryStyle.caption("BAGUIO FOREST REGISTRY"), RegistryStyle.heading("Create an account"));
        // Visibility Bindings
        tfPassVisible.setManaged(false); tfPassVisible.setVisible(false);
        tfConfirmVisible.setManaged(false); tfConfirmVisible.setVisible(false);
        tfPassVisible.textProperty().bindBidirectional(pfPass.textProperty());
        tfConfirmVisible.textProperty().bindBidirectional(pfConfirm.textProperty());

        CheckBox cbShow = new CheckBox("Show passwords");
        cbShow.setTextFill(Color.valueOf(COLOR_LEAF));
        cbShow.setStyle("-fx-font-size: 11px; -fx-cursor: hand;");
        cbShow.setOnAction(_ -> {
            boolean show = cbShow.isSelected();
            toggleVisibility(pfPass, tfPassVisible, show);
            toggleVisibility(pfConfirm, tfConfirmVisible, show);
        });

        // Strength Indicator
        HBox strengthBox = new HBox(10);
        strengthBox.setAlignment(Pos.CENTER_LEFT);
        Label sLabel = createHeaderLabel("Password strength");
        lblStrengthValue.setStyle("-fx-padding: 2 10 2 10; -fx-background-radius: 5; -fx-font-weight: bold; -fx-font-size: 10px;");
        strengthBox.getChildren().addAll(sLabel, lblStrengthValue);

        // Error Label styling
        lblError.setFont(Font.font("System", 11));
        lblError.setTextFill(Color.web("#B43232"));

        // Buttons
        Button btnRegister = new Button("Create account");
        stylePrimaryButton(btnRegister);
        btnRegister.setOnAction(_ -> performRegistration());

        Button btnCancel = new Button("Back to sign in");
        btnCancel.setStyle("-fx-background-color: transparent; -fx-text-fill: " + COLOR_LEAF + "; -fx-font-weight: bold; -fx-cursor: hand;");
        btnCancel.setOnAction(_ -> close());

        // Assembly
        card.getChildren().addAll(
                createHeaderLabel("Username"), tfUser,
                createHeaderLabel("Password"), new StackPane(pfPass, tfPassVisible),
                createHeaderLabel("Confirm password"), new StackPane(pfConfirm, tfConfirmVisible),
                cbShow, strengthBox, lblError, btnRegister, btnCancel
        );

        root.getChildren().add(card);

        // Listeners for Validation
        pfPass.textProperty().addListener((_, _, _) -> validateFields());
        pfConfirm.textProperty().addListener((_, _, _) -> validateFields());

        setScene(RegistryStyle.scene(root, 480, 660));
    }

    private void styleField(TextField f, String prompt) {
        f.setPromptText(prompt);
        f.setPrefHeight(40);
        f.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-border-color: transparent transparent #D1D1D1 transparent;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-padding: 5 0 5 0;"
        );
        f.focusedProperty().addListener((_, _, newVal) -> {
            if (newVal) f.setStyle(f.getStyle() + "-fx-border-color: transparent transparent " + COLOR_LEAF + " transparent;");
            else f.setStyle(f.getStyle() + "-fx-border-color: transparent transparent #D1D1D1 transparent;");
        });
    }

    private void stylePrimaryButton(Button b) {
        b.setPrefWidth(Double.MAX_VALUE);
        b.setPrefHeight(45);
        b.setStyle("-fx-background-color: " + COLOR_WOOD + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-cursor: hand;");
        b.setOnMouseEntered(_ -> b.setStyle(b.getStyle() + "-fx-background-color: " + COLOR_LEAF + ";"));
        b.setOnMouseExited(_ -> b.setStyle(b.getStyle() + "-fx-background-color: " + COLOR_WOOD + ";"));
    }

    private Label createHeaderLabel(String text) {
        Label l = new Label(text);
        // Sage Accent
        String COLOR_MOSS = "#90A955";
        l.setTextFill(Color.valueOf(COLOR_MOSS));
        l.setFont(Font.font("System", FontWeight.BOLD, 10));
        return l;
    }

    private void toggleVisibility(Control p, Control t, boolean show) {
        p.setVisible(!show); p.setManaged(!show);
        t.setVisible(show); t.setManaged(show);
    }

    // Logic Methods
    private void validateFields() {
        String pass = pfPass.getText();
        String confirm = pfConfirm.getText();
        updateStrengthIndicator(computePasswordScore(pass));
        lblError.setText((!confirm.isEmpty() && !pass.equals(confirm)) ? "Keys do not match." : " ");
    }

    private void performRegistration() {
        String user = tfUser.getText().trim();
        if (user.isEmpty() || pfPass.getText().isEmpty()) {
            lblError.setText("Entry incomplete: all fields required.");
            return;
        }
        try {
            if (!pfPass.getText().equals(pfConfirm.getText())) {
                lblError.setText("The passwords do not match."); return;
            }
            String resp = userService.register(user, pfPass.getText());
            if (resp.startsWith("SUCCESS")) {
                showAlert(Alert.AlertType.INFORMATION, "Account created", "Your ranger account is ready.");
                close();
            } else {
                lblError.setText(resp);
            }
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "System Error", "The registry service is unreachable.");
        }
    }

    private void updateStrengthIndicator(int score) {
        lblStrengthValue.setText(describeScore(score));
        String color = (score < 30) ? "#D24646" : (score < 60) ? "#E68C3C" : (score < 80) ? "#DCC850" : "#5FA564";
        lblStrengthValue.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-padding: 2 10 2 10; -fx-background-radius: 5;");
    }

    private int computePasswordScore(String pass) {
        if (pass == null || pass.isEmpty()) return 0;
        int s = 0;
        if (pass.length() >= 8) s += 25;
        if (pass.length() >= 12) s += 15;
        if (pass.matches(".*[0-9].*")) s += 20;
        if (pass.matches(".*[A-Z].*")) s += 20;
        if (pass.matches(".*[^a-zA-Z0-9].*")) s += 20;
        return Math.min(s, 100);
    }

    private String describeScore(int score) {
        if (score == 0) return "None";
        if (score < 30) return "Fragile";
        if (score < 60) return "Weak";
        if (score < 80) return "Sturdy";
        return "Resilient";
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.getDialogPane().setStyle("-fx-background-color: " + COLOR_BG + ";");
        alert.showAndWait();
    }
}
