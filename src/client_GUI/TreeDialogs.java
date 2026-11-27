package client_GUI;

import Model.Tree;
import Model.UserProfile;
import Server.TreeService;
import Server.UserService;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

class TreeDialogs {
    private TreeDialogs() {}

    // --- Deleted Trees View ---
    static String statusColor(String status) {
        String s = status == null ? "" : status.toLowerCase(java.util.Locale.ROOT);
        if (s.contains("healthy")) return "#4F772D";
        if (s.contains("monitor")) return "#457B9D";
        if (s.contains("risk")) return "#BC6C25";
        if (s.contains("destroy")) return "#9D2235";
        return "#6B8E23";
    }

    static class ProfileDialog extends BorderPane {
        private final TreeDashboard dashboard;
        private final UserService userService;
        private final String username;
        private final String sessionToken;

        private final TextField nameField = new TextField();
        private final ImageView avatarPreview = new ImageView();
        private byte[] pendingAvatarBytes = null;
        private final PasswordField currentPassField = new PasswordField();
        private final PasswordField newPassField = new PasswordField();
        private final PasswordField confirmPassField = new PasswordField();

        ProfileDialog(TreeDashboard parent, UserService userService, String username, String sessionToken, String sdgGreenColor, String panelDarkColor, String blueAccentColor) {
            this.userService = userService;
            this.username = username;
            this.sessionToken = sessionToken;

            this.dashboard = parent;



            BorderPane root = new BorderPane();
            root.setStyle("-fx-background-color: white;");
            root.setPadding(new Insets(10));

            Label title = new Label("My Profile");
            title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-padding: 10 10 0 10;");
            root.setTop(title);

            GridPane center = new GridPane();
            center.setStyle("-fx-background-color: white;");
            center.setHgap(10);
            center.setVgap(8);
            center.setPadding(new Insets(10));

            center.add(new Label("Display name:"), 0, 0);
            center.add(nameField, 1, 0);

            center.add(new Label("Avatar:"), 0, 1);
            HBox avatarPanel = new HBox(8);
            avatarPreview.setFitWidth(64);
            avatarPreview.setFitHeight(64);
            Button btnChangeAvatar = new Button("Change...");
            UIUtils.styleButton(btnChangeAvatar, blueAccentColor);
            btnChangeAvatar.setOnAction(_ -> chooseAvatarImage());
            avatarPanel.getChildren().addAll(avatarPreview, btnChangeAvatar);
            center.add(avatarPanel, 1, 1);

            center.add(new Label("Current password:"), 0, 2);
            center.add(currentPassField, 1, 2);

            center.add(new Label("New password:"), 0, 3);
            center.add(newPassField, 1, 3);

            center.add(new Label("Confirm new password:"), 0, 4);
            center.add(confirmPassField, 1, 4);

            root.setCenter(center);

            HBox footer = new HBox(10);
            footer.setAlignment(Pos.CENTER_RIGHT);
            Button btnSave = new Button("Save Changes");
            UIUtils.styleButton(btnSave, sdgGreenColor);
            btnSave.setOnAction(_ -> saveProfile());

            Button btnClose = new Button("Close");
            UIUtils.styleButton(btnClose, panelDarkColor);
            btnClose.setOnAction(_ -> dashboard.showRegistry());

            footer.getChildren().addAll(btnSave, btnClose);
            root.setBottom(footer);

            setCenter(root);
            loadCurrentProfile();
        }

        private void loadCurrentProfile() {
            new Thread(() -> {
                try {
                    UserProfile profile = userService.getProfile(sessionToken, username);
                    Platform.runLater(() -> {
                        if (profile.displayName != null && !profile.displayName.trim().isEmpty()) {
                            nameField.setText(profile.displayName);
                        } else {
                            nameField.setText(username);
                        }
                        if (profile.avatarBytes != null && profile.avatarBytes.length > 0) {
                            avatarPreview.setImage(UIUtils.bytesToImage(profile.avatarBytes));
                        }
                    });
                } catch (Exception ignored) {}
            }).start();
        }

        private void chooseAvatarImage() {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
            File file = chooser.showOpenDialog(dashboard);
            if (file == null || !file.exists()) return;

            long maxBytes = 5L * 1024L * 1024L;
            if (file.length() > maxBytes) {
                UIUtils.showAlert(Alert.AlertType.WARNING, "Avatar Too Large", "Avatar image is too large. Maximum size is 5 MB.");
                return;
            }

            try {
                byte[] data = Files.readAllBytes(file.toPath());
                pendingAvatarBytes = data;
                avatarPreview.setImage(UIUtils.bytesToImage(data));
            } catch (Exception ex) {
                UIUtils.showAlert(Alert.AlertType.ERROR, "Avatar Error", "Failed to load avatar: " + ex.getMessage());
            }
        }

        private void saveProfile() {
            String newName = nameField.getText().trim();
            String currentPw = currentPassField.getText().trim();
            String newPw = newPassField.getText().trim();
            String confirmPw = confirmPassField.getText().trim();

            if (!newPw.isEmpty() || !confirmPw.isEmpty() || !currentPw.isEmpty()) {
                if (currentPw.isEmpty() || newPw.isEmpty() || confirmPw.isEmpty()) {
                    UIUtils.showAlert(Alert.AlertType.WARNING, "Password Change", "To change your password, fill in current, new, and confirm fields.");
                    return;
                }
                if (!newPw.equals(confirmPw)) {
                    UIUtils.showAlert(Alert.AlertType.WARNING, "Password Change", "New password and confirmation do not match.");
                    return;
                }
            }

            new Thread(() -> {
                try {
                    String resp = userService.updateProfile(sessionToken, newName, pendingAvatarBytes);
                    String pwResp = null;
                    if (!newPw.isEmpty()) {
                        pwResp = userService.resetPassword(username, currentPw, newPw);
                    }
                    String finalPwResp = pwResp;
                    Platform.runLater(() -> {
                        StringBuilder sb = new StringBuilder(resp);
                        if (finalPwResp != null) sb.append("\n").append(finalPwResp);
                        UIUtils.showAlert(Alert.AlertType.INFORMATION, "Profile Update", sb.toString());
                    });
                    pendingAvatarBytes = null;
                } catch (Exception ex) {
                    Platform.runLater(() -> UIUtils.showAlert(Alert.AlertType.ERROR, "Profile Error", "Failed to update profile: " + ex.getMessage()));
                }
            }).start();
        }
    }

    // --- Tree Details ---
    static class TreeDetailDialog extends Stage {
        TreeDetailDialog(
                Window parent,
                Tree t,
                Predicate<Tree> canModifyTree,
                Consumer<Tree> editTree,
                String sdgGreenColor,
                String blueAccentColor,
                String panelDarkColor
        ) {
            initOwner(parent);
            initModality(Modality.WINDOW_MODAL);
            setTitle("Tree Details - #" + t.id);

            BorderPane root = new BorderPane();
            root.setStyle("-fx-background-color: white;");

            HBox header = new HBox();
            header.setStyle("-fx-background-color: " + sdgGreenColor + "; -fx-padding: 25;");
            Label title = new Label(t.species);
            title.setStyle("-fx-font-weight: bold; -fx-font-size: 28px; -fx-text-fill: white;");
            header.getChildren().add(title);
            root.setTop(header);

            GridPane body = new GridPane();
            body.setStyle("-fx-background-color: white; -fx-padding: 30;");
            body.setHgap(30);

            ColumnConstraints col1 = new ColumnConstraints();
            col1.setPercentWidth(45);
            ColumnConstraints col2 = new ColumnConstraints();
            col2.setPercentWidth(55);
            body.getColumnConstraints().addAll(col1, col2);

            StackPane imgPane = new StackPane();
            imgPane.setStyle("-fx-border-color: #DCDCDC; -fx-padding: 1;");
            ImageView imgView = new ImageView();
            imgView.setFitWidth(350);
            imgView.setFitHeight(350);
            imgView.setPreserveRatio(true);
            if (t.imageBytes != null && t.imageBytes.length > 0) {
                imgView.setImage(UIUtils.bytesToImage(t.imageBytes));
            }
            imgPane.getChildren().add(imgView);
            body.add(imgPane, 0, 0);

            VBox details = new VBox(15);
            details.setAlignment(Pos.TOP_LEFT);
            addDetailRow(details, "Tree ID", "#" + t.id);
            addDetailRow(details, "Location", t.location);
            addDetailRow(details, "Dimensions", t.height + "m (H) / " + t.diameter + "cm (Dia)");
            addDetailRow(details, "Planted Date", t.plantedDate);
            addDetailRow(details, "Status", t.status);

            TextArea notesArea = new TextArea(t.notes);
            notesArea.setEditable(false);
            notesArea.setWrapText(true);
            notesArea.setPrefHeight(120);
            notesArea.setStyle("-fx-control-inner-background: #F9F9F9; -fx-font-size: 13px;");

            VBox notesBox = new VBox(5);
            Label notesLbl = new Label("Notes");
            notesLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: gray;");
            notesBox.getChildren().addAll(notesLbl, notesArea);

            details.getChildren().add(notesBox);
            body.add(details, 1, 0);
            root.setCenter(body);

            HBox footer = new HBox(15);
            footer.setAlignment(Pos.CENTER_RIGHT);
            footer.setPadding(new Insets(20));

            if (canModifyTree != null && canModifyTree.test(t)) {
                Button btnEdit = new Button("Edit");
                UIUtils.styleButton(btnEdit, blueAccentColor);
                btnEdit.setPrefSize(100, 35);
                btnEdit.setOnAction(_ -> {
                    close();
                    Platform.runLater(() -> editTree.accept(t));
                });
                footer.getChildren().add(btnEdit);
            }

            Button btnClose = new Button("Close");
            UIUtils.styleButton(btnClose, panelDarkColor);
            btnClose.setPrefSize(100, 35);
            btnClose.setOnAction(_ -> close());

            footer.getChildren().add(btnClose);
            root.setBottom(footer);

            setScene(new Scene(root, 850, 650));
        }

        private void addDetailRow(VBox p, String label, String val) {
            TextFlow flow = new TextFlow();
            Text lbl = new Text(label + ": ");
            lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-fill: #404040;");
            Text value = new Text(val);
            value.setStyle("-fx-font-size: 15px; -fx-fill: #1a1a1a;");
            flow.getChildren().addAll(lbl, value);
            p.getChildren().add(flow);
        }
    }
}
