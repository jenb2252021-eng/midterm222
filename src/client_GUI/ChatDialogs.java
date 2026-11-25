package client_GUI;

import Model.ChatMessage;
import Model.UserProfile;
import Server.ChatService;
import Server.UserService;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.Callback;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Function;

// --- Members / Chat Dialogs (extracted from TreeDashboard) ---

    class MembersDialog extends BorderPane {
        private final ChatService chatService;
        private final String username;
        private final String sessionToken;
        private final ExecutorService dataExecutor;

        private final ObservableList<String> onlineModel = FXCollections.observableArrayList();
        private final ObservableList<String> offlineModel = FXCollections.observableArrayList();
        private final ListView<String> onlineList = new ListView<>(onlineModel);
        private final ListView<String> offlineList = new ListView<>(offlineModel);
        private final Label headerLabel = new Label("Members");
        private final Label onlineSectionLabel = new Label("Online (0)");
        private final Label offlineSectionLabel = new Label("Offline (0)");
        private final Timeline refreshTimer;
        private Set<String> onlineUsers = new HashSet<>();

        MembersDialog(
                Window parent,
                ChatService chatService,
                String username, String sessionToken,
                ExecutorService dataExecutor,
                Function<String, String> displayNameResolver,
                java.util.function.Consumer<String> openChatCallback,
                String panelDarkColor,
                String messageAccentColor
        ) {
            this.chatService = chatService;
            this.username = username;
        this.sessionToken = sessionToken;
            this.dataExecutor = dataExecutor;



            BorderPane root = new BorderPane();
            root.setStyle("-fx-background-color: #f5f7f5;");
            root.setPadding(new Insets(12));

            headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #2D5A27; -fx-padding: 8 8 6 8;");
            root.setTop(headerLabel);

            onlineSectionLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2D5A27; -fx-padding: 2 2 6 2;");
            offlineSectionLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #3C2F2F; -fx-padding: 10 2 6 2;");

            onlineList.setFixedCellSize(42);
            offlineList.setFixedCellSize(42);
            onlineList.setPlaceholder(new Label("No online users."));
            offlineList.setPlaceholder(new Label("No offline users."));
            onlineList.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #D9D9D9; -fx-border-radius: 8;");
            offlineList.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #D9D9D9; -fx-border-radius: 8;");

            Callback<ListView<String>, ListCell<String>> cellFactory = _ -> new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setStyle("-fx-background-color: transparent;");
                    } else {
                        boolean online = onlineUsers.contains(item);
                        String display = displayNameResolver != null ? displayNameResolver.apply(item) : item;
                        Circle dot = new Circle(5, online ? Color.web("#22C55E") : Color.web("#9CA3AF"));

                        Label nameLabel = new Label(display);
                        nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + (online ? "#1F2937" : "#6B7280") + ";");

                        Label stateLabel = new Label(online ? "Online" : "Offline");
                        stateLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + (online ? "#16A34A" : "#6B7280") + ";");

                        VBox textCol = new VBox(1, nameLabel, stateLabel);
                        textCol.setAlignment(Pos.CENTER_LEFT);

                        Region spacer = new Region();
                        HBox.setHgrow(spacer, Priority.ALWAYS);

                        Button btnMessage = new Button("\uD83D\uDCAC Message");
                        btnMessage.setFocusTraversable(false);
                        btnMessage.setStyle("-fx-background-color: " + messageAccentColor + "; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 14; -fx-padding: 4 10 4 10; -fx-cursor: hand;");
                        btnMessage.setOnAction(_ -> openChatCallback.accept(item));

                        HBox row = new HBox(10, dot, textCol, spacer, btnMessage);
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.setPadding(new Insets(4, 6, 4, 6));
                        setGraphic(row);
                        setText(null);
                        setStyle("-fx-background-color: transparent;");
                    }
                }
            };

            onlineList.setCellFactory(cellFactory);
            offlineList.setCellFactory(cellFactory);


            VBox listsPanel = new VBox();
            listsPanel.setStyle("-fx-background-color: transparent;");

            listsPanel.getChildren().addAll(onlineSectionLabel, onlineList, offlineSectionLabel, offlineList);

            ScrollPane scroll = new ScrollPane(listsPanel);
            scroll.setFitToWidth(true);
            scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
            root.setCenter(scroll);

            HBox footer = new HBox();
            footer.setAlignment(Pos.CENTER_RIGHT);
            footer.setPadding(new Insets(10, 0, 0, 0));
            Button btnClose = new Button("Close");
            UIUtils.styleButton(btnClose, panelDarkColor);
            btnClose.setOnAction(_ -> ((TreeDashboard) parent).showRegistry());
            footer.getChildren().add(btnClose);
            root.setBottom(footer);

            setCenter(root);

            refreshTimer = new Timeline(new KeyFrame(Duration.seconds(3), _ -> refreshMembers()));
            refreshTimer.setCycleCount(Timeline.INDEFINITE);
            refreshTimer.play();
            refreshMembers();

            sceneProperty().addListener((obs, oldScene, newScene) -> { if (newScene == null) refreshTimer.stop(); });
        }

        private void refreshMembers() {
            dataExecutor.execute(() -> {
                try {
                    ArrayList<String> all = chatService.getAllUsers(sessionToken);
                    ArrayList<String> online = chatService.getOnlineUsers(sessionToken);
                    Set<String> onlineSet = new HashSet<>(online);
                    Platform.runLater(() -> {
                        onlineUsers = onlineSet;
                        onlineModel.clear();
                        offlineModel.clear();
                        for (String user : all) {
                            if (user == null || user.trim().isEmpty() || user.equals(username)) continue;
                            if (onlineSet.contains(user)) onlineModel.add(user);
                            else offlineModel.add(user);
                        }
                        int totalMembers = onlineModel.size() + offlineModel.size();
                        headerLabel.setText("Members  -  Active: " + onlineModel.size() + " / " + totalMembers);
                        onlineSectionLabel.setText("Online (" + onlineModel.size() + ")");
                        offlineSectionLabel.setText("Offline (" + offlineModel.size() + ")");
                    });
                } catch (Exception ex) {
                    if (refreshTimer != null) refreshTimer.stop();
                }
            });
        }
    }

    class ChatDialog extends BorderPane {
        private final String username;
        private final String sessionToken;
        private final ChatService chatService;
        private final UserService userService;
        private final ExecutorService dataExecutor;

        private final String peerUsername;
        private final ObservableList<ChatMessage> messageModel = FXCollections.observableArrayList();
        private final ListView<ChatMessage> messageList = new ListView<>(messageModel);
        private final TextArea inputArea = new TextArea();
        private final Label chatMetaLabel = new Label("Active now: --");
        private final Timeline refreshTimer;

        private String selfDisplayName = null;
        private String peerDisplayName = null;
        private Image selfAvatar = null;
        private Image peerAvatar = null;

        ChatDialog(
                Window parent,
                String peerUsername,
                String username, String sessionToken,
                boolean isAdmin,
                ChatService chatService,
                UserService userService,
                ExecutorService dataExecutor,
                String panelDarkColor,
                String blueAccentColor,
                String sdgGreenColor
        ) {
            this.peerUsername = peerUsername;
            this.username = username;
        this.sessionToken = sessionToken;
            this.chatService = chatService;
            this.userService = userService;
            this.dataExecutor = dataExecutor;



            BorderPane root = new BorderPane();
            root.setStyle("-fx-background-color: #F5F7F5;");

            BorderPane headerPanel = new BorderPane();
            headerPanel.setStyle("-fx-background-color: #254d39; -fx-padding: 8 8 5 8;");

            Button btnBack = new Button("\u2190 Back");
            UIUtils.styleButton(btnBack, panelDarkColor);
            btnBack.setOnAction(_ -> ((TreeDashboard) parent).openMembersDialog());

            Label header = new Label("Chat with " + peerUsername);
            header.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: white; -fx-padding: 0 0 0 10;");
            chatMetaLabel.setStyle("-fx-text-fill: #B9BBBE; -fx-font-size: 11px; -fx-padding: 0 6 0 0;");

            headerPanel.setLeft(btnBack);
            headerPanel.setCenter(header);
            headerPanel.setRight(chatMetaLabel);
            root.setTop(headerPanel);

            loadProfiles();

            messageList.setStyle("-fx-background-color: #F5F7F5;");

            messageList.setCellFactory(_ -> new ListCell<>() {
                {
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
                @Override
                protected void updateItem(ChatMessage value, boolean empty) {
                    super.updateItem(value, empty);
                    if (empty || value == null) {
                        setText(null);
                        setGraphic(null);
                        setStyle("-fx-background-color: #F5F7F5;");
                    } else {
                        setStyle(isSelected() ? "-fx-background-color: #e3ece5;" : "-fx-background-color: #F5F7F5;");

                        boolean isMine = username.equals(value.sender);
                        String senderName = isMine
                                ? (selfDisplayName != null ? selfDisplayName : username)
                                : (peerDisplayName != null ? peerDisplayName : value.sender);
                        String text = value.content;
                        if (value.deleted && (text == null || text.trim().isEmpty())) text = "[message deleted]";
                        if (value.edited && !value.deleted) text = text + " (edited)";

                        TextFlow textFlow = new TextFlow();
                        textFlow.setMaxWidth(280);
                        textFlow.setTextAlignment(isMine ? TextAlignment.LEFT : TextAlignment.RIGHT);
                        Text contentTxt = new Text(text != null ? text : "");
                        contentTxt.setStyle("-fx-fill: #213c2b;");
                        textFlow.getChildren().add(contentTxt);

                        Text timestampTxt = new Text("\n" + (value.timestamp == null ? "" : value.timestamp));
                        timestampTxt.setStyle("-fx-fill: #607468; -fx-font-size: 9px;");
                        textFlow.getChildren().add(timestampTxt);

                        if (isMine && value.readByRecipient && value.readAt != null) {
                            Text readTxt = new Text("\nRead: " + value.readAt);
                            readTxt.setStyle("-fx-fill: #57f287; -fx-font-size: 9px;");
                            textFlow.getChildren().add(readTxt);
                        }

                        VBox contentBox = new VBox(textFlow);
                        contentBox.setMaxWidth(300);
                        contentBox.setStyle("-fx-background-color: " + (isMine ? "#d9eee2" : "#ffffff") + "; -fx-background-radius: 14; -fx-padding: 8 10 8 10;");
                        contentBox.setAlignment(isMine ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);

                        if ("IMAGE".equalsIgnoreCase(value.messageType) && value.fileBase64 != null && !value.fileBase64.trim().isEmpty()) {
                            try {
                                byte[] bytes = Base64.getDecoder().decode(value.fileBase64);
                                ImageView imgView = new ImageView(UIUtils.bytesToImage(bytes));
                                imgView.setFitWidth(260);
                                imgView.setPreserveRatio(true);
                                contentBox.getChildren().add(imgView);
                            } catch (Exception ignored) {
                            }
                        }

                        ImageView avatarView = new ImageView(isMine ? selfAvatar : peerAvatar);
                        avatarView.setFitWidth(32);
                        avatarView.setFitHeight(32);

                        Label edgeName = new Label(isMine ? "You" : senderName);
                        edgeName.setStyle("-fx-text-fill: #476953; -fx-font-size: 10px; -fx-font-weight: bold;");

                        VBox edgeIdentity = new VBox(3, avatarView, edgeName);
                        edgeIdentity.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

                        HBox wrapper = new HBox(12);
                        wrapper.setPadding(new Insets(6, 10, 6, 10));
                        wrapper.prefWidthProperty().bind(messageList.widthProperty().subtract(22));
                        wrapper.setMaxWidth(Double.MAX_VALUE);
                        Region spacer = new Region();
                        HBox.setHgrow(spacer, Priority.ALWAYS);

                        if (isMine) {
                            wrapper.getChildren().addAll(spacer, contentBox, edgeIdentity);
                        } else {
                            wrapper.getChildren().addAll(edgeIdentity, contentBox, spacer);
                        }

                        setText(null);
                        setGraphic(wrapper);
                    }
                }
            });

            ContextMenu popup = new ContextMenu();
            MenuItem editItem = new MenuItem("Edit");
            MenuItem deleteItem = new MenuItem("Delete / Unsend");
            MenuItem saveImageItem = new MenuItem("Save image as...");
            popup.getItems().addAll(editItem, deleteItem, saveImageItem);

            messageList.setOnContextMenuRequested(e -> {
                ChatMessage msg = messageList.getSelectionModel().getSelectedItem();
                if (msg == null) return;
                boolean canModify = username.equals(msg.sender) || isAdmin;
                editItem.setDisable(!username.equals(msg.sender) || msg.deleted);
                deleteItem.setDisable(!canModify || msg.deleted);
                boolean isImage = "IMAGE".equalsIgnoreCase(msg.messageType) && msg.fileBase64 != null && !msg.fileBase64.trim().isEmpty();
                saveImageItem.setDisable(!isImage);
                if (canModify || isImage) popup.show(messageList, e.getScreenX(), e.getScreenY());
            });

            editItem.setOnAction(_ -> {
                ChatMessage msg = messageList.getSelectionModel().getSelectedItem();
                if (msg == null) return;
                TextInputDialog dialog = new TextInputDialog(msg.content != null ? msg.content : "");
                dialog.setTitle("Edit");
                dialog.setHeaderText("Edit message:");
                dialog.showAndWait().ifPresent(updated -> dataExecutor.execute(() -> {
                    try {
                        chatService.editMessage(sessionToken, msg.id, updated);
                        loadMessages();
                    } catch (Exception ignored) {}
                }));
            });

            deleteItem.setOnAction(_ -> {
                ChatMessage msg = messageList.getSelectionModel().getSelectedItem();
                if (msg == null) return;
                Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete / unsend this message for everyone?", ButtonType.YES, ButtonType.NO);
                a.showAndWait().ifPresent(res -> {
                    if (res == ButtonType.YES) {
                        dataExecutor.execute(() -> {
                            try {
                                chatService.deleteMessage(sessionToken, msg.id, true);
                                loadMessages();
                            } catch (Exception ignored) {}
                        });
                    }
                });
            });

            saveImageItem.setOnAction(_ -> {
                ChatMessage msg = messageList.getSelectionModel().getSelectedItem();
                if (msg == null || !"IMAGE".equalsIgnoreCase(msg.messageType) || msg.fileBase64 == null) return;
                FileChooser chooser = new FileChooser();
                if (msg.fileName != null) chooser.setInitialFileName(msg.fileName);
                File out = chooser.showSaveDialog(getScene().getWindow());
                if (out == null) return;
                dataExecutor.execute(() -> {
                    try {
                        byte[] data = Base64.getDecoder().decode(msg.fileBase64);
                        Files.write(out.toPath(), data);
                    } catch (Exception ignored) {}
                });
            });

            root.setCenter(messageList);

            BorderPane inputPanel = new BorderPane();
            inputPanel.setPadding(new Insets(5, 10, 10, 10));
            inputPanel.setStyle("-fx-background-color: #254d39;");

            inputArea.setWrapText(true);
            inputArea.setStyle("-fx-control-inner-background: #e3ece5; -fx-text-fill: white;");
            inputArea.setPrefRowCount(3);
            inputPanel.setCenter(inputArea);

            VBox rightButtons = new VBox(4);
            rightButtons.setPadding(new Insets(0, 0, 0, 5));
            Button btnAttachImage = new Button("Image");
            UIUtils.styleButton(btnAttachImage, blueAccentColor);
            btnAttachImage.setMaxWidth(Double.MAX_VALUE);
            btnAttachImage.setOnAction(_ -> attachImage());

            Button btnSend = new Button("Send");
            UIUtils.styleButton(btnSend, sdgGreenColor);
            btnSend.setMaxWidth(Double.MAX_VALUE);
            btnSend.setOnAction(_ -> sendMessage());

            rightButtons.getChildren().addAll(btnAttachImage, btnSend);
            inputPanel.setRight(rightButtons);

            root.setBottom(inputPanel);

            setCenter(root);

            refreshTimer = new Timeline(new KeyFrame(Duration.millis(1500), _ -> loadMessages()));
            refreshTimer.setCycleCount(Timeline.INDEFINITE);
            refreshTimer.play();
            loadMessages();

            sceneProperty().addListener((obs, oldScene, newScene) -> { if (newScene == null) refreshTimer.stop(); });
        }

        private void loadProfiles() {
            new Thread(() -> {
                try {
                    UserProfile me = userService.getProfile(sessionToken, username);
                    UserProfile peer = userService.getProfile(sessionToken, peerUsername);
                    Platform.runLater(() -> {
                        selfDisplayName = (me != null && me.displayName != null && !me.displayName.trim().isEmpty()) ? me.displayName : username;
                        peerDisplayName = (peer != null && peer.displayName != null && !peer.displayName.trim().isEmpty()) ? peer.displayName : peerUsername;
                        if (me != null && me.avatarBytes != null && me.avatarBytes.length > 0) selfAvatar = UIUtils.bytesToImage(me.avatarBytes);
                        if (peer != null && peer.avatarBytes != null && peer.avatarBytes.length > 0) peerAvatar = UIUtils.bytesToImage(peer.avatarBytes);
                    });
                } catch (Exception ignored) {}
            }).start();
        }

        private void sendMessage() {
            String text = inputArea.getText().trim();
            if (text.isEmpty()) return;
            inputArea.setText("");
            dataExecutor.execute(() -> {
                try {
                    chatService.sendMessage(sessionToken, peerUsername, text);
                    loadMessages();
                } catch (Exception ignored) {}
            });
        }

        private void attachImage() {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
            File file = chooser.showOpenDialog(getScene().getWindow());
            if (file == null || !file.exists()) return;
            if (file.length() > 20L * 1024L * 1024L) {
                UIUtils.showAlert(Alert.AlertType.WARNING, "Image Too Large", "Maximum size is 20 MB.");
                return;
            }

            dataExecutor.execute(() -> {
                try {
                    byte[] data = Files.readAllBytes(file.toPath());
                    String caption = inputArea.getText().trim();
                    Platform.runLater(() -> inputArea.setText(""));
                    chatService.sendAttachment(sessionToken, peerUsername, file.getName(), "IMAGE", data, caption);
                    loadMessages();
                } catch (Exception ignored) {}
            });
        }

        private void loadMessages() {
            dataExecutor.execute(() -> {
                try {
                    ArrayList<ChatMessage> msgs = chatService.getConversation(sessionToken, peerUsername);
                    Set<String> onlineSet = new HashSet<>(chatService.getOnlineUsers(sessionToken));
                    int activeCount = onlineSet.size();
                    boolean peerOnline = onlineSet.contains(peerUsername);
                    Platform.runLater(() -> {
                        boolean wasNearBottom = isNearBottom();
                        if (hasConversationChanged(msgs)) {
                            messageModel.setAll(msgs);
                            if (!messageModel.isEmpty() && (wasNearBottom || messageModel.size() <= 1)) {
                                messageList.scrollTo(messageModel.size() - 1);
                            }
                        }
                        chatMetaLabel.setText("Active now: " + activeCount + " | " + (peerOnline ? "Peer online" : "Peer offline"));
                    });
                } catch (Exception ignored) {}
            });
        }

        private boolean hasConversationChanged(List<ChatMessage> incoming) {
            if (incoming == null) {
                return !messageModel.isEmpty();
            }
            if (incoming.size() != messageModel.size()) {
                return true;
            }
            for (int i = 0; i < incoming.size(); i++) {
                String oldSig = messageFingerprint(messageModel.get(i));
                String newSig = messageFingerprint(incoming.get(i));
                if (!Objects.equals(oldSig, newSig)) {
                    return true;
                }
            }
            return false;
        }

        private String messageFingerprint(ChatMessage m) {
            if (m == null) {
                return "";
            }
            return Objects.toString(m.id, "") + "|" +
                    Objects.toString(m.sender, "") + "|" +
                    Objects.toString(m.recipient, "") + "|" +
                    Objects.toString(m.messageType, "") + "|" +
                    Objects.toString(m.content, "") + "|" +
                    Objects.toString(m.fileName, "") + "|" +
                    Objects.toString(m.fileBase64, "") + "|" +
                    Objects.toString(m.timestamp, "") + "|" +
                    m.edited + "|" +
                    Objects.toString(m.editedAt, "") + "|" +
                    m.deleted + "|" +
                    Objects.toString(m.deletedAt, "") + "|" +
                    Objects.toString(m.deletedBy, "") + "|" +
                    m.readByRecipient + "|" +
                    Objects.toString(m.readAt, "");
        }

        private boolean isNearBottom() {
            ScrollBar vertical = getVerticalScrollBar();
            if (vertical == null) {
                return true;
            }
            double range = vertical.getMax() - vertical.getMin();
            if (range <= 0.0) {
                return true;
            }
            double remaining = vertical.getMax() - vertical.getValue();
            return remaining <= Math.max(0.5, range * 0.08);
        }

        private ScrollBar getVerticalScrollBar() {
            for (Node node : messageList.lookupAll(".scroll-bar")) {
                if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL) {
                    return bar;
                }
            }
            return null;
        }

    }
