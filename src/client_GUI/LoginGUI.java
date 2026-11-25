package client_GUI;

import Server.UserService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.rmi.registry.Registry;

public class LoginGUI extends Stage {
    private final TextField fUser = new TextField();
    private final PasswordField fPass = new PasswordField();

    private final Registry registry;
    private UserService userService;

    public LoginGUI(Registry registry) {
        this.registry = registry;
        try {
            this.userService = (UserService) registry.lookup("UserService");
        } catch (Exception e) {
            e.printStackTrace();
        }

        setTitle("The Lorax · Baguio Forest Registry");
        BorderPane root = new BorderPane();
        root.getStyleClass().add("login-background");
        HBox brand = RegistryStyle.brand(false);
        brand.setPadding(new Insets(22, 32, 10, 32));
        root.setTop(brand);
        VBox card = new VBox(12);
        card.getStyleClass().add("login-card");
        card.setMaxSize(396, Region.USE_PREF_SIZE);
        Label intro = new Label("Sign in to monitor the forest, wherever the trail takes you.");
        intro.setWrapText(true);
        intro.getStyleClass().add("muted");
        Button login = RegistryStyle.primary("Sign In  →", this::handleLogin);
        login.setMaxWidth(Double.MAX_VALUE);
        login.setDefaultButton(true);
        Button register = new Button("New ranger? Create an account");
        register.getStyleClass().add("link-button");
        register.setMaxWidth(Double.MAX_VALUE);
        register.setOnAction(e -> handleRegister());
        HBox connection = new HBox(10, new Label(userService == null ? "Local server unavailable" : "Local server connected"), RegistryStyle.spacer(), new Label(ClientConfig.getServerIP()));
        connection.getStyleClass().add("connection");
        card.getChildren().addAll(RegistryStyle.leaf(), RegistryStyle.caption("BAGUIO FOREST REGISTRY"),
                RegistryStyle.heading("Welcome back."), intro, new Label("Username"), fUser,
                new Label("Password"), fPass, login, register, new Separator(), connection);
        root.setCenter(new StackPane(card));
        Label footer = new Label("Offline-ready · Local network");
        footer.getStyleClass().add("muted");
        BorderPane.setAlignment(footer, Pos.CENTER);
        BorderPane.setMargin(footer, new Insets(12));
        root.setBottom(footer);
        setScene(RegistryStyle.scene(root, 1100, 720));
        centerOnScreen();
    }

    private void handleLogin() {
        String u = fUser.getText().trim();
        String p = fPass.getText();
        try {
            String response = userService.authenticate(u, p);
            if (response.startsWith("SUCCESS")) {
                new TreeDashboard(registry, u, response.substring("SUCCESS: ".length())).show();
                close();
            } else { showAlert("Link Failed", response); }
        } catch (Exception ex) { showAlert("System Error", "The forest network is down."); }
    }

    private void handleRegister() { new RegisterDialog(this, userService).showAndWait(); }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.initOwner(this);
        alert.showAndWait();
    }

}
