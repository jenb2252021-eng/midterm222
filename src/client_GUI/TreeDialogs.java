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
    static class DeletedTreesDialog extends BorderPane {
        private final TreeDashboard dashboard;
        DeletedTreesDialog(TreeDashboard parent, List<Tree> deletedTrees, String panelDarkColor) {
            this.dashboard = parent;





            BorderPane root = new BorderPane();
            root.setStyle("-fx-background-color: white;");

            Label header = new Label("Archive");
            header.getStyleClass().addAll("heading","topbar");
            header.setMaxWidth(Double.MAX_VALUE);
            root.setTop(header);

            VBox listPanel = new VBox(0);
            listPanel.setStyle("-fx-background-color: white;");

            if (deletedTrees.isEmpty()) {
                Label empty = new Label("No deleted trees found.");
                empty.setPadding(new Insets(20));
                listPanel.getChildren().add(empty);
            } else {
                for (Tree t : deletedTrees) {
                    listPanel.getChildren().add(createDeletedRow(t));
                }
            }

            ScrollPane scroll = new ScrollPane(listPanel);
            scroll.setFitToWidth(true);
            root.setCenter(scroll);

            HBox footer = new HBox();
            footer.setAlignment(Pos.CENTER_RIGHT);
            footer.setPadding(new Insets(15));
            Button btnClose = new Button("Close");
            UIUtils.styleButton(btnClose, panelDarkColor);
            btnClose.setOnAction(_ -> dashboard.showRegistry());
            footer.getChildren().add(btnClose);
            root.setBottom(footer);

            setCenter(root);
        }

        private BorderPane createDeletedRow(Tree t) {
            BorderPane row = new BorderPane();
            row.setStyle("-fx-background-color: white; -fx-border-color: #E6E6E6; -fx-border-width: 0 0 1 0; -fx-padding: 15 30 15 30;");

            ImageView thumb = new ImageView();
            thumb.setFitWidth(60);
            thumb.setFitHeight(60);
            thumb.setPreserveRatio(true);
            if (t.imageBytes != null && t.imageBytes.length > 0) {
                thumb.setImage(UIUtils.bytesToImage(t.imageBytes));
            }

            VBox info = new VBox(5);
            Label l1 = new Label(t.species + " #" + t.id);
            l1.setStyle("-fx-font-weight: bold;");
            Label l2 = new Label("Location: " + t.location + " | Status: " + t.status);
            l2.setStyle("-fx-text-fill: gray;");
            info.getChildren().addAll(l1, l2);

            row.setLeft(thumb);
            row.setCenter(info);
            BorderPane.setMargin(info, new Insets(0, 0, 0, 20));
            return row;
        }
    }

    // --- Pending Trees Review ---
    static class PendingTreesDialog extends BorderPane {
        private final TreeDashboard dashboard;
        private final TreeService treeService;
        private final String username;
        private final String sessionToken;
        private final Function<String, String> displayNameResolver;
        private final BiConsumer<Throwable, String> remoteFailureHandler;
        private final Runnable onLoadInitialData;
        private final Runnable onFetchAndShowPending;
        private final Runnable onResetLastPendingCountSeen;

        PendingTreesDialog(
                TreeDashboard parent,
                List<Tree> pendingTrees,
                TreeService treeService,
                String username, String sessionToken,
                Function<String, String> displayNameResolver,
                BiConsumer<Throwable, String> remoteFailureHandler,
                Runnable onLoadInitialData,
                Runnable onFetchAndShowPending,
                Runnable onResetLastPendingCountSeen,
                String blueAccentColor,
                String panelDarkColor,
                String sdgGreenColor,
                String deleteRedColor
        ) {
            this.treeService = treeService;
            this.username = username;
            this.sessionToken = sessionToken;
            this.displayNameResolver = displayNameResolver;
            this.remoteFailureHandler = remoteFailureHandler;
            this.onLoadInitialData = onLoadInitialData;
            this.onFetchAndShowPending = onFetchAndShowPending;
            this.onResetLastPendingCountSeen = onResetLastPendingCountSeen;

            this.dashboard = parent;


            BorderPane root = new BorderPane();
            VBox header = new VBox(4, RegistryStyle.heading("Pending Review"), new Label(pendingTrees.size() + (pendingTrees.size() == 1 ? " field submission awaits your review" : " field submissions awaiting your review")));
            header.getStyleClass().add("topbar"); root.setTop(header);
            ListView<Tree> choices = new ListView<>();
            choices.setPrefWidth(220); choices.getItems().setAll(pendingTrees);
            choices.setCellFactory(list -> new ListCell<>() {
                @Override protected void updateItem(Tree t, boolean empty) {
                    super.updateItem(t,empty);
                    setText(empty || t == null ? null : t.species + "\n" + t.ownerUsername + " · " + t.status);
                }
            });
            choices.setPlaceholder(new Label("No pending submissions"));
            root.setLeft(choices);
            root.setCenter(new Label("All submissions reviewed."));
            choices.getSelectionModel().selectedItemProperty().addListener((obs, oldTree, t) -> {
                if (t == null) return;
                VBox detail = new VBox(14);
                detail.setPadding(new Insets(24));
                if (t.imageBytes != null && t.imageBytes.length > 0) {
                    ImageView photo = new ImageView(UIUtils.bytesToImage(t.imageBytes));
                    photo.setPreserveRatio(true); photo.setFitHeight(160); detail.getChildren().add(photo);
                }
                Label notes = new Label(t.notes); notes.setWrapText(true);
                detail.getChildren().addAll(RegistryStyle.heading(t.species),
                    new Label("Submitted by " + displayNameResolver.apply(t.ownerUsername)),
                    new Separator(), new Label("Height  " + t.height + " m      Diameter  " + t.diameter + " cm"),
                    new Label(t.status + "   ·   #" + t.id), RegistryStyle.caption("FIELD NOTES"), notes,
                    new Label("Planted  " + t.plantedDate), new Label(t.location));
                root.setCenter(new ScrollPane(detail));
                Button reject = new Button("Reject"); reject.setOnAction(e -> rejectPendingTree(t));
                HBox actions = new HBox(10, reject, RegistryStyle.primary("Approve", () -> approvePendingTree(t)));
                actions.setAlignment(Pos.CENTER_RIGHT); actions.setPadding(new Insets(15)); root.setBottom(actions);
            });
            choices.getSelectionModel().selectFirst();
            setCenter(root);
        }

        private void approvePendingTree(Tree t) {
            try {
                String resp = treeService.approvePendingTree(t.id, sessionToken);
                UIUtils.showAlert(resp.startsWith("SUCCESS") ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR, "Approve Pending Tree", resp);
                if (resp.startsWith("SUCCESS")) {
                    onLoadInitialData.run();
                    onFetchAndShowPending.run();
                    onResetLastPendingCountSeen.run();
                }
            } catch (Exception ex) {
                if (remoteFailureHandler != null) remoteFailureHandler.accept(ex, "Failed to approve tree");
            }
        }

        private void rejectPendingTree(Tree t) {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Reject this pending tree (" + t.species + ")?", ButtonType.YES, ButtonType.NO);
            a.showAndWait().ifPresent(res -> {
                if (res == ButtonType.YES) {
                    try {
                        String resp = treeService.rejectPendingTree(t.id, sessionToken);
                        UIUtils.showAlert(resp.startsWith("SUCCESS") ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR, "Reject Pending Tree", resp);
                        if (resp.startsWith("SUCCESS")) {
                            onFetchAndShowPending.run();
                            onResetLastPendingCountSeen.run();
                        }
                    } catch (Exception ex) {
                        if (remoteFailureHandler != null) remoteFailureHandler.accept(ex, "Failed to reject tree");
                    }
                }
            });
        }
    }

    // --- Account Management (Admin only) ---
    static class AccountManagerDialog extends BorderPane {
        private final TreeDashboard dashboard;
        private final UserService userService;
        private final String username;
        private final String sessionToken;
        private final BiConsumer<Throwable, String> remoteFailureHandler;

        private final ObservableList<String> userListModel = FXCollections.observableArrayList();
        private final ListView<String> userList = new ListView<>(userListModel);

        AccountManagerDialog(
                TreeDashboard parent,
                UserService userService,
                String username, String sessionToken,
                BiConsumer<Throwable, String> remoteFailureHandler,
                String blueAccentColor,
                String deleteRedColor,
                String panelDarkColor
        ) {
            this.userService = userService;
            this.username = username;
            this.sessionToken = sessionToken;
            this.remoteFailureHandler = remoteFailureHandler;

            this.dashboard = parent;





            BorderPane root = new BorderPane();
            Label header = new Label("Manage Users");
            header.getStyleClass().addAll("heading","topbar");
            header.setMaxWidth(Double.MAX_VALUE);
            root.setTop(header);

            root.setCenter(userList);

            HBox buttons = new HBox(10);
            buttons.setAlignment(Pos.CENTER_RIGHT);
            buttons.setPadding(new Insets(10));
            Button btnChangePass = new Button("Change Password");
            UIUtils.styleButton(btnChangePass, blueAccentColor);
            btnChangePass.setOnAction(_ -> handleChangePassword());

            Button btnDelete = new Button("Delete Account");
            UIUtils.styleButton(btnDelete, deleteRedColor);
            btnDelete.setOnAction(_ -> handleDeleteAccount());

            Button btnClose = new Button("Close");
            UIUtils.styleButton(btnClose, panelDarkColor);
            btnClose.setOnAction(_ -> dashboard.showRegistry());

            buttons.getChildren().addAll(btnChangePass, btnDelete, btnClose);
            root.setBottom(buttons);

            setCenter(root);
            loadUsers();
        }

        private void loadUsers() {
            userListModel.clear();
            try {
                userListModel.addAll(userService.getAllUsernames(sessionToken));
            } catch (Exception ex) {
                if (remoteFailureHandler != null) remoteFailureHandler.accept(ex, "Failed to load users");
            }
        }

        private String getSelectedUsername() {
            String sel = userList.getSelectionModel().getSelectedItem();
            if (sel == null) UIUtils.showAlert(Alert.AlertType.WARNING, "Select", "Please select a user first.");
            return sel;
        }

        private void handleChangePassword() {
            String target = getSelectedUsername();
            if (target == null) return;

            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Change Password");
            dialog.setHeaderText("Enter new password for '" + target + "':");
            dialog.showAndWait().ifPresent(newPass -> {
                if (newPass.isEmpty()) {
                    UIUtils.showAlert(Alert.AlertType.WARNING, "Error", "Password cannot be empty.");
                    return;
                }
                try {
                    String resp = userService.adminSetPassword(sessionToken, target, newPass);
                    UIUtils.showAlert(resp.startsWith("SUCCESS") ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR, "Change Password", resp);
                } catch (Exception ex) {
                    if (remoteFailureHandler != null) remoteFailureHandler.accept(ex, "Failed to change password");
                }
            });
        }

        private void handleDeleteAccount() {
            String target = getSelectedUsername();
            if (target == null) return;
            if ("admin".equalsIgnoreCase(target)) {
                UIUtils.showAlert(Alert.AlertType.WARNING, "Action Denied", "The primary admin account cannot be deleted.");
                return;
            }

            Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete user account '" + target + "'?\nThis cannot be undone.",
                    ButtonType.YES, ButtonType.NO);
            a.showAndWait().ifPresent(res -> {
                if (res == ButtonType.YES) {
                    try {
                        String resp = userService.deleteUser(sessionToken, target);
                        UIUtils.showAlert(resp.startsWith("SUCCESS") ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR, "Delete Account", resp);
                        if (resp.startsWith("SUCCESS")) loadUsers();
                    } catch (Exception ex) {
                        if (remoteFailureHandler != null) remoteFailureHandler.accept(ex, "Failed to delete account");
                    }
                }
            });
        }
    }

    // --- Profile (Ranger only) ---
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
