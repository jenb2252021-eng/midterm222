package client_GUI;

import Model.ChatMessage;
import Model.UserProfile;
import Server.ChatService;
import Server.UserService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * Two-panel messaging workspace: people on the left (search, online status, unread counts), the conversation on
 * the right (chat bubbles, day separators, timestamps, image previews, send state) with a composer below.
 * <p>
 * Uses the existing {@link ChatService}: conversations refresh every few seconds without reloading the page,
 * opening a conversation marks it read (server side), and "Seen" comes from the stored read receipt. Sending is
 * optimistic: the bubble appears at once as "Sending…", then "Sent" (or "Seen" later), or "Failed" with Retry.
 * Edit, delete/unsend and "Save image" from the previous chat screen are kept in each bubble's menu.
 * </p>
 */
final class MessagesView extends SplitPane {

    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024; // same limit as the server
    private static final int MAX_TEXT = 2000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatService chat;
    private final UserService users;
    private final String me;
    private final String token;
    private final boolean isAdmin;
    private final ExecutorService io;
    private final Function<String, String> displayName;

    // ---- people
    private final ObservableList<String> people = FXCollections.observableArrayList();
    private final ListView<String> peopleList = new ListView<>(people);
    private final TextField search = new TextField();
    private final ToggleButton onlyOnline = new ToggleButton("Online");
    private List<String> allUsers = new ArrayList<>();
    private Set<String> online = new HashSet<>();
    private Map<String, Integer> unread = new HashMap<>();
    private final Map<String, Image> avatars = new HashMap<>();
    private final Label peopleCount = new Label();
    private final Label connection = new Label();

    // ---- conversation
    private final StackPane right = new StackPane();
    private String peer;
    private final VBox bubbles = new VBox(6);
    private final ScrollPane scroll = new ScrollPane(bubbles);
    private final Label peerName = new Label();
    private final Label peerStatus = new Label();
    private final StackPane peerAvatar = new StackPane();
    private final Label offlineBanner = new Label();
    private final TextArea input = new TextArea();
    private final Label counter = new Label();
    private final Button send = new Button("Send");
    private final HBox attachmentPreview = new HBox(10);
    private File pendingImage;
    private List<ChatMessage> shown = new ArrayList<>();
    private String shownFingerprint = "";
    private final List<Outgoing> outgoing = new ArrayList<>();

    private final Timeline poll;

    /** A message the user sent that the server hasn't confirmed yet (or that failed). */
    private static final class Outgoing {
        final String localId = UUID.randomUUID().toString();
        final String peer, text;
        final File image;
        final String time = LocalDateTime.now().format(STAMP);
        boolean failed;
        String error;
        Outgoing(String peer, String text, File image) { this.peer = peer; this.text = text; this.image = image; }
    }

    MessagesView(ChatService chat, UserService users, String me, String token, boolean isAdmin,
                 ExecutorService io, Function<String, String> displayName) {
        this.chat = chat;
        this.users = users;
        this.me = me;
        this.token = token;
        this.isAdmin = isAdmin;
        this.io = io;
        this.displayName = displayName;
        getStyleClass().add("messages");
        getItems().addAll(buildPeoplePanel(), right);
        setDividerPositions(0.3);
        SplitPane.setResizableWithParent(getItems().get(0), false);
        showEmptyConversation();

        poll = new Timeline(new KeyFrame(Duration.seconds(2.5), e -> refresh()));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();
        sceneProperty().addListener((o, a, s) -> { if (s == null) poll.stop(); else poll.play(); });
        refresh();
    }

    /** Opens a conversation directly (e.g. from elsewhere in the app). */
    void openConversation(String user) {
        if (user == null || user.equals(me)) return;
        peer = user;
        shownFingerprint = "";
        peopleList.getSelectionModel().select(user);
        buildConversation();
        loadConversation(true);
    }

    // =====================================================================================================
    // People panel
    // =====================================================================================================

    private Node buildPeoplePanel() {
        Label title = new Label("Messages");
        title.getStyleClass().add("section-title");
        peopleCount.getStyleClass().add("muted");
        HBox head = new HBox(8, title, RegistryStyle.spacer(), peopleCount);
        head.setAlignment(Pos.CENTER_LEFT);

        search.setPromptText("Search people");
        search.getStyleClass().add("search-field");
        search.textProperty().addListener((o, a, b) -> applyPeopleFilter());
        SVGPath glass = new SVGPath();
        glass.setContent("M7 1a6 6 0 1 0 3.9 10.6l3.8 3.8 1.1-1.1-3.8-3.8A6 6 0 0 0 7 1zm0 1.6a4.4 4.4 0 1 1 0 8.8 4.4 4.4 0 0 1 0-8.8z");
        glass.setStyle("-fx-fill: #7c8a82;");
        glass.setMouseTransparent(true);
        StackPane searchBox = new StackPane(search, glass);
        StackPane.setAlignment(glass, Pos.CENTER_LEFT);
        StackPane.setMargin(glass, new Insets(0, 0, 0, 12));

        onlyOnline.getStyleClass().add("chip");
        onlyOnline.setGraphic(new Circle(4, Color.web("#2fa36f")));
        onlyOnline.setTooltip(new Tooltip("Show only people who are signed in now"));
        onlyOnline.setOnAction(e -> applyPeopleFilter());
        HBox filters = new HBox(6, onlyOnline);

        peopleList.getStyleClass().add("people-list");
        peopleList.setPlaceholder(placeholder("No people found", "Try another name, or clear the Online filter."));
        peopleList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) { setGraphic(null); setText(null); return; }
                boolean on = online.contains(u);
                Node av = avatar(u, 38, on);
                Label name = new Label(displayName.apply(u));
                name.getStyleClass().add("person-name");
                Label status = new Label(on ? "Online" : "Offline");
                status.getStyleClass().add(on ? "status-online" : "muted");
                if (!u.equals(displayName.apply(u))) status.setText((on ? "Online" : "Offline") + " · @" + u);
                VBox text = new VBox(1, name, status);
                HBox row = new HBox(10, av, text, RegistryStyle.spacer());
                row.setAlignment(Pos.CENTER_LEFT);
                int n = unread.getOrDefault(u, 0);
                if (n > 0) {
                    Label badge = new Label(n > 99 ? "99+" : String.valueOf(n));
                    badge.getStyleClass().add("unread-badge");
                    row.getChildren().add(badge);
                    name.setStyle("-fx-font-weight: bold;");
                }
                setGraphic(row);
                setText(null);
                setOpacity(on ? 1 : 0.78);
            }
        });
        peopleList.getSelectionModel().selectedItemProperty().addListener((o, a, u) -> {
            if (u != null && !u.equals(peer)) openConversation(u);
        });
        VBox.setVgrow(peopleList, Priority.ALWAYS);

        connection.getStyleClass().add("muted");
        connection.setVisible(false);
        connection.setManaged(false);

        VBox panel = new VBox(12, head, searchBox, filters, connection, peopleList);
        panel.getStyleClass().add("people-panel");
        panel.setPadding(new Insets(20, 14, 14, 18));
        panel.setMinWidth(250);
        panel.setPrefWidth(300);
        panel.setMaxWidth(380);
        return panel;
    }

    private void applyPeopleFilter() {
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<String> list = new ArrayList<>();
        for (String u : allUsers) {
            if (u == null || u.isBlank() || u.equals(me)) continue;
            if (onlyOnline.isSelected() && !online.contains(u)) continue;
            String dn = displayName.apply(u).toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !u.toLowerCase(Locale.ROOT).contains(q) && !dn.contains(q)) continue;
            list.add(u);
        }
        // Unread first, then online, then alphabetical.
        list.sort(Comparator.comparing((String u) -> -unread.getOrDefault(u, 0))
                .thenComparing(u -> !online.contains(u))
                .thenComparing(u -> displayName.apply(u).toLowerCase(Locale.ROOT)));
        String keep = peer;
        if (!list.equals(new ArrayList<>(people))) {
            people.setAll(list);
            if (keep != null && list.contains(keep)) peopleList.getSelectionModel().select(keep);
        } else {
            peopleList.refresh();
        }
        long on = allUsers.stream().filter(u -> !u.equals(me) && online.contains(u)).count();
        peopleCount.setText(on + " online");
    }

    // =====================================================================================================
    // Polling
    // =====================================================================================================

    private void refresh() {
        io.execute(() -> {
            try {
                List<String> all = chat.getAllUsers(token);
                Set<String> on = new HashSet<>(chat.getOnlineUsers(token));
                Map<String, Integer> counts = chat.getUnreadCountsBySender(token);
                for (String u : all) if (!avatars.containsKey(u)) loadAvatar(u);
                Platform.runLater(() -> {
                    allUsers = all;
                    online = on;
                    unread = counts;
                    if (peer != null) unread.remove(peer); // the open conversation is being read
                    connection.setVisible(false);
                    connection.setManaged(false);
                    applyPeopleFilter();
                    updatePeerHeader();
                });
                if (peer != null) loadConversation(false);
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    connection.setText("Can't reach the server. Retrying…");
                    connection.setVisible(true);
                    connection.setManaged(true);
                });
            }
        });
    }

    private void loadAvatar(String u) {
        avatars.put(u, null); // mark as requested
        try {
            UserProfile p = users.getProfile(token, u);
            if (p != null && p.avatarBytes != null && p.avatarBytes.length > 0) {
                Image img = new Image(new ByteArrayInputStream(p.avatarBytes));
                if (!img.isError()) Platform.runLater(() -> { avatars.put(u, img); peopleList.refresh(); updatePeerHeader(); });
            }
        } catch (Exception ignored) {
        }
    }

    private void loadConversation(boolean forceScroll) {
        String with = peer;
        if (with == null) return;
        io.execute(() -> {
            try {
                List<ChatMessage> msgs = chat.getConversation(token, with);
                Platform.runLater(() -> {
                    if (!with.equals(peer)) return;
                    String fp = fingerprint(msgs);
                    if (!fp.equals(shownFingerprint) || forceScroll) {
                        boolean atBottom = forceScroll || isNearBottom();
                        shown = msgs;
                        shownFingerprint = fp;
                        renderMessages();
                        if (atBottom) scrollToBottom();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (with.equals(peer) && shown.isEmpty()) {
                        bubbles.getChildren().setAll(placeholder("Couldn't load this conversation", "Check the connection; it will retry automatically."));
                    }
                });
            }
        });
    }

    private static String fingerprint(List<ChatMessage> msgs) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : msgs) {
            sb.append(m.id).append(m.edited).append(m.deleted).append(m.readByRecipient)
                    .append(m.content == null ? 0 : m.content.hashCode()).append('|');
        }
        return sb.toString();
    }

    // =====================================================================================================
    // Conversation
    // =====================================================================================================

    private void showEmptyConversation() {
        SVGPath icon = new SVGPath();
        icon.setContent("M6 10h36v24H18l-8 8v-8H6z M14 18h20 M14 25h14");
        icon.setStyle("-fx-fill: #eef5f0; -fx-stroke: #20865b; -fx-stroke-width: 2; -fx-stroke-line-join: round;");
        icon.setScaleX(1.6);
        icon.setScaleY(1.6);
        Label t = new Label("Select a conversation");
        t.getStyleClass().add("section-title");
        Label s = new Label("Choose someone on the left to coordinate field work. Online rangers see messages straight away;\n"
                + "offline rangers see them the next time they sign in.");
        s.getStyleClass().add("muted");
        s.setWrapText(true);
        s.setMaxWidth(420);
        s.setStyle("-fx-text-alignment: center;");
        VBox box = new VBox(16, icon, t, s);
        box.setAlignment(Pos.CENTER);
        right.getChildren().setAll(box);
        right.getStyleClass().add("conversation-empty");
    }

    private void buildConversation() {
        right.getStyleClass().remove("conversation-empty");
        // Header: avatar, name, status.
        peerName.getStyleClass().add("person-title");
        VBox who = new VBox(2, peerName, peerStatus);
        HBox header = new HBox(12, peerAvatar, who);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("conversation-header");
        offlineBanner.getStyleClass().add("offline-banner");
        offlineBanner.setMaxWidth(Double.MAX_VALUE);
        offlineBanner.managedProperty().bind(offlineBanner.visibleProperty());

        bubbles.setPadding(new Insets(16, 20, 16, 20));
        bubbles.setFillWidth(true);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("conversation-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        bubbles.heightProperty().addListener((o, a, b) -> { if (stickToBottom) scroll.setVvalue(1.0); });
        scroll.vvalueProperty().addListener((o, a, v) -> stickToBottom = v.doubleValue() > 0.97);
        bubbles.getChildren().setAll(loadingRow());

        VBox page = new VBox(header, offlineBanner, scroll, buildComposer());
        VBox.setVgrow(scroll, Priority.ALWAYS);
        page.getStyleClass().add("conversation");
        right.getChildren().setAll(page);
        updatePeerHeader();
        Platform.runLater(input::requestFocus);
    }

    private boolean stickToBottom = true;

    private void updatePeerHeader() {
        if (peer == null) return;
        boolean on = online.contains(peer);
        peerName.setText(displayName.apply(peer));
        peerStatus.setText(on ? "Online now" : "Offline");
        peerStatus.getStyleClass().setAll("label", on ? "status-online" : "muted");
        peerAvatar.getChildren().setAll(avatar(peer, 40, on));
        offlineBanner.setText(displayName.apply(peer) + " is offline. Your messages will be waiting when they sign in.");
        offlineBanner.setVisible(!on);
    }

    private Node buildComposer() {
        input.setPromptText("Write a message…  (Enter to send, Shift+Enter for a new line)");
        input.setWrapText(true);
        input.setPrefRowCount(2);
        input.getStyleClass().add("composer-input");
        input.textProperty().addListener((o, a, t) -> {
            if (t != null && t.length() > MAX_TEXT) input.setText(t.substring(0, MAX_TEXT));
            updateComposerState();
        });
        input.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                sendCurrent();
            }
        });
        Button attach = new Button();
        attach.setGraphic(icon("M14 6l-6.5 6.5a2.5 2.5 0 0 0 3.5 3.5L18 9a4 4 0 0 0-5.7-5.7L5 10.6a5.5 5.5 0 0 0 7.8 7.8L18 13"));
        attach.getStyleClass().add("composer-icon");
        attach.setTooltip(new Tooltip("Attach an image (PNG, JPG, GIF or BMP, up to 20 MB)"));
        attach.setOnAction(e -> chooseImage());
        send.getStyleClass().add("primary");
        send.setDefaultButton(false);
        send.setOnAction(e -> sendCurrent());
        counter.getStyleClass().add("muted");
        attachmentPreview.setAlignment(Pos.CENTER_LEFT);
        attachmentPreview.managedProperty().bind(attachmentPreview.visibleProperty());
        attachmentPreview.setVisible(false);
        HBox row = new HBox(10, attach, input, send);
        row.setAlignment(Pos.BOTTOM_LEFT);
        HBox.setHgrow(input, Priority.ALWAYS);
        HBox foot = new HBox(counter);
        foot.setAlignment(Pos.CENTER_RIGHT);
        VBox composer = new VBox(8, attachmentPreview, row, foot);
        composer.getStyleClass().add("composer");
        updateComposerState();
        return composer;
    }

    private void updateComposerState() {
        String t = input.getText() == null ? "" : input.getText();
        send.setDisable(t.isBlank() && pendingImage == null);
        counter.setText(t.length() > MAX_TEXT * 0.8 ? t.length() + " / " + MAX_TEXT : "");
    }

    /** Picks an image, validates type and size, and shows a preview before sending. */
    private void chooseImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Attach an image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File f = chooser.showOpenDialog(getScene().getWindow());
        if (f == null) return;
        if (f.length() > MAX_IMAGE_BYTES) {
            Toast.error("That image is " + (f.length() / (1024 * 1024)) + " MB. The limit is 20 MB.");
            return;
        }
        Image img = new Image(f.toURI().toString(), 120, 90, true, true, true);
        ProgressIndicator spin = new ProgressIndicator();
        spin.setMaxSize(28, 28);
        ImageView preview = new ImageView(img);
        preview.setFitHeight(64);
        preview.setPreserveRatio(true);
        StackPane thumb = new StackPane(spin, preview);
        img.progressProperty().addListener((o, a, p) -> spin.setVisible(p.doubleValue() < 1));
        img.errorProperty().addListener((o, a, err) -> {
            if (err) {
                Toast.error("That file isn't a readable image.");
                clearAttachment();
            }
        });
        Label name = new Label(f.getName() + "\n" + String.format(Locale.US, "%.1f MB", f.length() / 1048576.0));
        name.getStyleClass().add("muted");
        Button remove = new Button("✕");
        remove.getStyleClass().add("icon-button");
        remove.setTooltip(new Tooltip("Remove attachment"));
        remove.setOnAction(e -> clearAttachment());
        attachmentPreview.getChildren().setAll(thumb, name, remove);
        attachmentPreview.getStyleClass().setAll("attachment-preview");
        attachmentPreview.setVisible(true);
        pendingImage = f;
        updateComposerState();
    }

    private void clearAttachment() {
        pendingImage = null;
        attachmentPreview.getChildren().clear();
        attachmentPreview.setVisible(false);
        updateComposerState();
    }

    private void sendCurrent() {
        if (peer == null) return;
        String text = input.getText() == null ? "" : input.getText().trim();
        if (text.isEmpty() && pendingImage == null) return;
        Outgoing out = new Outgoing(peer, text, pendingImage);
        input.clear();
        clearAttachment();
        outgoing.add(out);
        renderMessages();
        scrollToBottom();
        deliver(out);
    }

    private void deliver(Outgoing out) {
        out.failed = false;
        io.execute(() -> {
            try {
                if (out.image != null) {
                    byte[] data = Files.readAllBytes(out.image.toPath());
                    chat.sendAttachment(token, out.peer, out.image.getName(), "IMAGE", data, out.text);
                } else {
                    chat.sendMessage(token, out.peer, out.text);
                }
                List<ChatMessage> msgs = chat.getConversation(token, out.peer);
                Platform.runLater(() -> {
                    outgoing.remove(out);
                    if (out.peer.equals(peer)) {
                        shown = msgs;
                        shownFingerprint = fingerprint(msgs);
                        renderMessages();
                        scrollToBottom();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    out.failed = true;
                    out.error = ex.getMessage() == null ? "Not sent" : ex.getMessage().replace("ERROR:", "").trim();
                    renderMessages();
                });
            }
        });
    }

    // =====================================================================================================
    // Rendering
    // =====================================================================================================

    private void renderMessages() {
        bubbles.getChildren().clear();
        if (shown.isEmpty() && outgoing.stream().noneMatch(o -> o.peer.equals(peer))) {
            bubbles.getChildren().add(placeholder("No messages yet", "Say hello to " + displayName.apply(peer) + "."));
            return;
        }
        LocalDate lastDay = null;
        for (ChatMessage m : shown) {
            LocalDate day = dayOf(m.timestamp);
            if (day != null && !day.equals(lastDay)) {
                bubbles.getChildren().add(daySeparator(day));
                lastDay = day;
            }
            bubbles.getChildren().add(bubble(m));
        }
        for (Outgoing o : outgoing) {
            if (o.peer.equals(peer)) bubbles.getChildren().add(pendingBubble(o));
        }
    }

    private Node bubble(ChatMessage m) {
        boolean mine = me.equals(m.sender);
        VBox body = new VBox(6);
        body.getStyleClass().addAll("bubble", mine ? "bubble-mine" : "bubble-theirs");
        boolean isImage = "IMAGE".equalsIgnoreCase(m.messageType) && m.fileBase64 != null && !m.fileBase64.isBlank() && !m.deleted;
        if (m.deleted) {
            Label gone = new Label("Message deleted");
            gone.getStyleClass().add("bubble-deleted");
            body.getChildren().add(gone);
        } else {
            if (isImage) body.getChildren().add(imageThumb(m));
            if (m.content != null && !m.content.isBlank()) {
                Label text = new Label(m.content);
                text.setWrapText(true);
                text.getStyleClass().add("bubble-text");
                body.getChildren().add(text);
            }
        }
        String meta = timeOf(m.timestamp) + (m.edited && !m.deleted ? " · edited" : "");
        if (mine && !m.deleted) meta += m.readByRecipient ? "  ·  Seen" : "  ·  Sent";
        Label metaLabel = new Label(meta);
        metaLabel.getStyleClass().add("bubble-meta");
        body.getChildren().add(metaLabel);
        body.setMaxWidth(440);
        attachMenu(body, m, mine, isImage);
        return align(body, mine);
    }

    private Node pendingBubble(Outgoing o) {
        VBox body = new VBox(6);
        body.getStyleClass().addAll("bubble", "bubble-mine", o.failed ? "bubble-failed" : "bubble-pending");
        if (o.image != null) {
            ImageView iv = new ImageView(new Image(o.image.toURI().toString(), 240, 180, true, true, true));
            body.getChildren().add(iv);
        }
        if (!o.text.isEmpty()) {
            Label text = new Label(o.text);
            text.setWrapText(true);
            text.getStyleClass().add("bubble-text");
            body.getChildren().add(text);
        }
        if (o.failed) {
            Label err = new Label("Not sent: " + o.error);
            err.getStyleClass().add("bubble-meta");
            err.setWrapText(true);
            Hyperlink retry = new Hyperlink("Retry");
            retry.setOnAction(e -> { deliver(o); renderMessages(); });
            Hyperlink discard = new Hyperlink("Discard");
            discard.setOnAction(e -> { outgoing.remove(o); renderMessages(); });
            body.getChildren().addAll(err, new HBox(8, retry, discard));
        } else {
            ProgressIndicator p = new ProgressIndicator();
            p.setMaxSize(12, 12);
            Label meta = new Label("Sending…", p);
            meta.getStyleClass().add("bubble-meta");
            body.getChildren().add(meta);
        }
        body.setMaxWidth(440);
        return align(body, true);
    }

    private Node imageThumb(ChatMessage m) {
        try {
            byte[] bytes = Base64.getDecoder().decode(m.fileBase64);
            Image img = new Image(new ByteArrayInputStream(bytes));
            ImageView iv = new ImageView(img);
            iv.setPreserveRatio(true);
            iv.setFitWidth(Math.min(260, img.getWidth()));
            iv.setCursor(javafx.scene.Cursor.HAND);
            Tooltip.install(iv, new Tooltip((m.fileName == null ? "Image" : m.fileName) + " · click to enlarge"));
            iv.setOnMouseClicked(e -> openImage(img, m.fileName));
            return iv;
        } catch (Exception e) {
            Label broken = new Label("Image couldn't be shown");
            broken.getStyleClass().add("bubble-deleted");
            return broken;
        }
    }

    private void openImage(Image img, String name) {
        Stage s = new Stage();
        s.initOwner(getScene().getWindow());
        s.initModality(Modality.WINDOW_MODAL);
        s.setTitle(name == null ? "Image" : name);
        ImageView iv = new ImageView(img);
        iv.setPreserveRatio(true);
        iv.setFitWidth(Math.min(1000, img.getWidth()));
        iv.setFitHeight(Math.min(760, img.getHeight()));
        StackPane root = new StackPane(iv);
        root.setStyle("-fx-background-color: #111; -fx-padding: 12;");
        root.setOnMouseClicked(e -> s.close());
        s.setScene(new javafx.scene.Scene(root));
        s.show();
    }

    /** Right-click menu on a bubble: copy, edit (own text), delete/unsend (own, or admin), save image. */
    private void attachMenu(Node bubble, ChatMessage m, boolean mine, boolean isImage) {
        ContextMenu menu = new ContextMenu();
        if (!m.deleted && m.content != null && !m.content.isBlank()) {
            MenuItem copy = new MenuItem("Copy text");
            copy.setOnAction(e -> { ClipboardContent c = new ClipboardContent(); c.putString(m.content); Clipboard.getSystemClipboard().setContent(c); });
            menu.getItems().add(copy);
        }
        if (mine && !m.deleted && !isImage) {
            MenuItem edit = new MenuItem("Edit…");
            edit.setOnAction(e -> {
                TextInputDialog d = new TextInputDialog(m.content == null ? "" : m.content);
                d.initOwner(getScene().getWindow());
                d.setTitle("Edit message");
                d.setHeaderText(null);
                d.setContentText("Message:");
                d.showAndWait().map(String::trim).filter(s -> !s.isEmpty() && !s.equals(m.content)).ifPresent(updated -> io.execute(() -> {
                    try {
                        chat.editMessage(token, m.id, updated);
                        Platform.runLater(() -> Toast.success("Message edited."));
                        loadConversation(false);
                    } catch (Exception ex) {
                        Platform.runLater(() -> Toast.error("Couldn't edit the message: " + ex.getMessage()));
                    }
                }));
            });
            menu.getItems().add(edit);
        }
        if ((mine || isAdmin) && !m.deleted) {
            MenuItem del = new MenuItem(mine ? "Unsend…" : "Delete for everyone…");
            del.setOnAction(e -> {
                Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Remove this message for everyone in the conversation?", ButtonType.CANCEL, ButtonType.OK);
                a.initOwner(getScene().getWindow());
                a.setHeaderText(null);
                if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
                io.execute(() -> {
                    try {
                        String r = chat.deleteMessage(token, m.id, true);
                        Platform.runLater(() -> { if (r != null && r.startsWith("ERROR")) Toast.error(r); else Toast.success("Message removed."); });
                        loadConversation(false);
                    } catch (Exception ex) {
                        Platform.runLater(() -> Toast.error("Couldn't remove the message: " + ex.getMessage()));
                    }
                });
            });
            menu.getItems().add(del);
        }
        if (isImage) {
            MenuItem save = new MenuItem("Save image as…");
            save.setOnAction(e -> {
                FileChooser chooser = new FileChooser();
                if (m.fileName != null) chooser.setInitialFileName(m.fileName);
                File out = chooser.showSaveDialog(getScene().getWindow());
                if (out == null) return;
                io.execute(() -> {
                    try {
                        Files.write(out.toPath(), Base64.getDecoder().decode(m.fileBase64));
                        Platform.runLater(() -> Toast.success("Image saved to " + out.getName() + "."));
                    } catch (Exception ex) {
                        Platform.runLater(() -> Toast.error("Couldn't save the image."));
                    }
                });
            });
            menu.getItems().add(save);
        }
        if (!menu.getItems().isEmpty()) {
            bubble.setOnContextMenuRequested(e -> { menu.show(bubble, e.getScreenX(), e.getScreenY()); e.consume(); });
        }
    }

    private static Node align(Node bubble, boolean mine) {
        HBox row = new HBox(bubble);
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    private static Node daySeparator(LocalDate day) {
        LocalDate today = LocalDate.now();
        String text = day.equals(today) ? "Today" : day.equals(today.minusDays(1)) ? "Yesterday"
                : day.format(DateTimeFormatter.ofPattern(day.getYear() == today.getYear() ? "EEE, MMM d" : "MMM d, yyyy"));
        Label l = new Label(text);
        l.getStyleClass().add("day-separator");
        HBox row = new HBox(l);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(8, 0, 4, 0));
        return row;
    }

    private static LocalDate dayOf(String ts) {
        try { return LocalDateTime.parse(ts, STAMP).toLocalDate(); } catch (Exception e) { return null; }
    }

    private static String timeOf(String ts) {
        try { return LocalDateTime.parse(ts, STAMP).format(DateTimeFormatter.ofPattern("h:mm a")); }
        catch (Exception e) { return ts == null ? "" : ts; }
    }

    private boolean isNearBottom() {
        return scroll.getVvalue() > 0.95 || bubbles.getHeight() <= scroll.getViewportBounds().getHeight();
    }

    private void scrollToBottom() {
        stickToBottom = true;
        Platform.runLater(() -> { scroll.layout(); scroll.setVvalue(1.0); });
    }

    // =====================================================================================================
    // Small pieces
    // =====================================================================================================

    /** Round avatar: profile photo when available, otherwise the initial on a colour; status dot in the corner. */
    private Node avatar(String user, double size, boolean on) {
        Circle c = new Circle(size / 2);
        Image img = avatars.get(user);
        StackPane face;
        if (img != null) {
            c.setFill(new ImagePattern(img));
            face = new StackPane(c);
        } else {
            String[] palette = {"#20865B", "#457B9D", "#BC6C25", "#6B5B95", "#2F6F6A", "#8A5A44"};
            c.setFill(Color.web(palette[Math.floorMod(user.hashCode(), palette.length)]));
            String dn = displayName.apply(user);
            Label initial = new Label(dn.isBlank() ? "?" : dn.substring(0, 1).toUpperCase(Locale.ROOT));
            initial.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: " + (size * 0.42) + "px;");
            face = new StackPane(c, initial);
        }
        Circle dot = new Circle(size * 0.14, on ? Color.web("#2fa36f") : Color.web("#b4bfb8"));
        dot.setStroke(Color.WHITE);
        dot.setStrokeWidth(2);
        StackPane.setAlignment(dot, Pos.BOTTOM_RIGHT);
        face.getChildren().add(dot);
        face.setMinSize(size, size);
        face.setMaxSize(size, size);
        return face;
    }

    private static Node icon(String path) {
        SVGPath p = new SVGPath();
        p.setContent(path);
        p.setStyle("-fx-fill: transparent; -fx-stroke: #42534a; -fx-stroke-width: 1.6; -fx-stroke-line-cap: round; -fx-stroke-line-join: round;");
        return p;
    }

    private static Node placeholder(String title, String sub) {
        Label t = new Label(title);
        t.getStyleClass().add("section-title");
        Label s = new Label(sub);
        s.getStyleClass().add("muted");
        s.setWrapText(true);
        VBox box = new VBox(6, t, s);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        return box;
    }

    private static Node loadingRow() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));
        for (int i = 0; i < 4; i++) {
            Region r = new Region();
            r.getStyleClass().add("skeleton");
            r.setPrefSize(i % 2 == 0 ? 260 : 200, 38);
            r.setMaxWidth(Region.USE_PREF_SIZE);
            HBox row = new HBox(r);
            row.setAlignment(i % 2 == 0 ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
            box.getChildren().add(row);
        }
        Skeleton.pulse(box);
        return box;
    }
}
