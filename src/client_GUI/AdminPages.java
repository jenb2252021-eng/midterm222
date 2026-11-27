package client_GUI;

import Model.Tree;
import Model.UserProfile;
import Server.AdminService;
import Server.ChatService;
import Server.TreeService;
import Server.UserService;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The administration pages (Pending Review, Manage Users, Archive, Ranger Access, System States), built on one
 * layout: a page header, a toolbar (search and filters), a list or table, and a details panel for the selection.
 * Every page loads in the background with a skeleton, shows an empty or error state when needed, asks before any
 * change that matters, and confirms results with a toast. All data comes from the existing services and the
 * server activity log; nothing is sample data.
 */
final class AdminPages {

    /** One parsed activity-log line: "[timestamp] User: u | Action: A | Details: d". */
    record LogEntry(String time, String user, String action, String details) {
        private static final Pattern LINE = Pattern.compile("^\\[(.*?)\\] User: (.*?) \\| Action: (.*?) \\| Details: (.*)$");
        static LogEntry parse(String line) {
            Matcher m = LINE.matcher(line == null ? "" : line);
            return m.matches() ? new LogEntry(m.group(1), m.group(2), m.group(3), m.group(4)) : null;
        }
    }

    private final TreeService trees;
    private final UserService users;
    private final AdminService admin;
    private final ChatService chat;
    private final String me;
    private final String token;
    private final ExecutorService io;
    private final Function<String, String> displayName;
    private final Runnable refreshInventory;
    private final Runnable reopenPending;
    private final Consumer<String> openMessagesWith;
    private final Function<String, Long> treesRecordedBy;

    AdminPages(TreeService trees, UserService users, AdminService admin, ChatService chat, String me, String token,
               ExecutorService io, Function<String, String> displayName, Runnable refreshInventory,
               Runnable reopenPending, Consumer<String> openMessagesWith, Function<String, Long> treesRecordedBy) {
        this.trees = trees;
        this.users = users;
        this.admin = admin;
        this.chat = chat;
        this.me = me;
        this.token = token;
        this.io = io;
        this.displayName = displayName;
        this.refreshInventory = refreshInventory;
        this.reopenPending = reopenPending;
        this.openMessagesWith = openMessagesWith;
        this.treesRecordedBy = treesRecordedBy;
    }

    // =====================================================================================================
    // Shared layout
    // =====================================================================================================

    private static BorderPane page(String title, Label subtitle, Node... actions) {
        VBox titles = new VBox(4, RegistryStyle.heading(title), subtitle);
        subtitle.getStyleClass().add("muted");
        HBox header = new HBox(12, titles, RegistryStyle.spacer());
        header.getChildren().addAll(actions);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("topbar");
        BorderPane p = new BorderPane();
        p.setTop(header);
        return p;
    }

    private static TextField searchField(String prompt) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        f.getStyleClass().add("search-field");
        f.setPrefWidth(280);
        return f;
    }

    private static StackPane withGlass(TextField f) {
        javafx.scene.shape.SVGPath glass = new javafx.scene.shape.SVGPath();
        glass.setContent("M7 1a6 6 0 1 0 3.9 10.6l3.8 3.8 1.1-1.1-3.8-3.8A6 6 0 0 0 7 1zm0 1.6a4.4 4.4 0 1 1 0 8.8 4.4 4.4 0 0 1 0-8.8z");
        glass.setStyle("-fx-fill: #7c8a82;");
        glass.setMouseTransparent(true);
        StackPane s = new StackPane(f, glass);
        StackPane.setAlignment(glass, Pos.CENTER_LEFT);
        StackPane.setMargin(glass, new Insets(0, 0, 0, 12));
        s.setMaxWidth(300);
        return s;
    }

    /** Segmented toggle group; returns the buttons in order. */
    private static List<ToggleButton> segments(Consumer<Integer> onSelect, String... labels) {
        ToggleGroup g = new ToggleGroup();
        List<ToggleButton> out = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            ToggleButton b = new ToggleButton(labels[i]);
            b.setToggleGroup(g);
            b.getStyleClass().add("segment");
            if (i == 0) b.getStyleClass().add("segment-left");
            if (i == labels.length - 1) b.getStyleClass().add("segment-right");
            int idx = i;
            b.setOnAction(e -> { if (!b.isSelected()) b.setSelected(true); onSelect.accept(idx); });
            out.add(b);
        }
        out.get(0).setSelected(true);
        return out;
    }

    private static VBox detailCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("detail-card");
        card.setMinWidth(300);
        card.setPrefWidth(360);
        card.setMaxWidth(420);
        return card;
    }

    private static Label pill(String text, String color) {
        Label l = new Label(text, new Circle(4, Color.web(color)));
        l.getStyleClass().add("status-pill");
        l.setStyle("-fx-text-fill: " + color + "; -fx-background-color: " + color + "1A;");
        return l;
    }

    private static GridPane facts(String[][] rows) {
        GridPane g = new GridPane();
        g.setHgap(22);
        g.setVgap(9);
        for (int i = 0; i < rows.length; i++) {
            Label k = new Label(rows[i][0]);
            k.getStyleClass().add("muted");
            k.setMinWidth(Region.USE_PREF_SIZE);
            Label v = new Label(rows[i][1] == null || rows[i][1].isBlank() ? "—" : rows[i][1]);
            v.setWrapText(true);
            g.add(k, 0, i);
            g.add(v, 1, i);
        }
        return g;
    }

    private boolean confirm(Node owner, String title, String message, String okText, boolean danger) {
        ButtonType ok = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        Alert a = new Alert(danger ? Alert.AlertType.WARNING : Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ok);
        if (owner.getScene() != null) a.initOwner(owner.getScene().getWindow());
        a.setTitle(title);
        a.setHeaderText(title);
        return a.showAndWait().orElse(ButtonType.CANCEL) == ok;
    }

    /** Runs a server call off the UI thread, then shows the result as a toast (or an error toast). */
    private void act(String working, Callable call, Runnable after) {
        Toast.info(working);
        io.execute(() -> {
            try {
                String r = call.run();
                Platform.runLater(() -> {
                    if (r != null && r.startsWith("ERROR")) Toast.error(r.replace("ERROR:", "").trim());
                    else { Toast.success(r == null ? "Done." : r); if (after != null) after.run(); }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> Toast.error(ex.getMessage() == null ? "The server didn't respond." : ex.getMessage().replace("ERROR:", "").trim()));
            }
        });
    }

    interface Callable { String run() throws Exception; }

    private List<LogEntry> readLog() throws Exception {
        List<LogEntry> out = new ArrayList<>();
        for (String line : admin.getActivityLog(token)) {
            LogEntry e = LogEntry.parse(line);
            if (e != null) out.add(e);
        }
        return out;
    }

    // =====================================================================================================
    // Pending Review
    // =====================================================================================================

    Node pending(List<Tree> pendingTrees) {
        Label subtitle = new Label(pendingTrees.isEmpty() ? "You're all caught up."
                : pendingTrees.size() + (pendingTrees.size() == 1 ? " field submission awaits your review" : " field submissions await your review"));
        Button refresh = RegistryStyle.secondary("Refresh", reopenPending);
        BorderPane page = page("Pending Review", subtitle, refresh);

        Map<String, String> submittedAt = new HashMap<>();
        ObservableList<Tree> queueItems = FXCollections.observableArrayList(pendingTrees);
        FilteredList<Tree> filtered = new FilteredList<>(queueItems, t -> true);
        ListView<Tree> queue = new ListView<>(filtered);
        queue.setPrefWidth(320);
        queue.getStyleClass().add("queue-list");
        queue.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(Tree t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setGraphic(null); setText(null); return; }
                Label name = new Label(t.species + "  #" + t.id);
                name.setStyle("-fx-font-weight: bold;");
                Label who = new Label("by " + displayName.apply(t.ownerUsername) + "  ·  " + Optional.ofNullable(submittedAt.get(key(t))).orElse("submitted date loading…"));
                who.getStyleClass().add("muted");
                VBox text = new VBox(4, name, who, pill(t.status, TreeDialogs.statusColor(t.status)));
                setGraphic(text);
                setText(null);
            }
        });
        queue.setPlaceholder(new Label("No submissions match."));
        TextField search = searchField("Search species, ID or ranger");
        search.textProperty().addListener((o, a, q) -> {
            String s = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(t -> s.isEmpty() || (t.species + " " + t.id + " " + t.ownerUsername).toLowerCase(Locale.ROOT).contains(s));
        });

        StackPane detail = new StackPane();
        HBox actions = new HBox(10);
        actions.getStyleClass().add("action-bar");
        actions.setAlignment(Pos.CENTER_RIGHT);
        queue.getSelectionModel().selectedItemProperty().addListener((o, a, t) -> showPendingDetail(t, detail, actions, submittedAt));

        // Approved / Rejected history from the activity log.
        StackPane decided = new StackPane();
        HBox body = new HBox(16, queue, detail);
        HBox.setHgrow(detail, Priority.ALWAYS);
        StackPane content = new StackPane(body);
        List<ToggleButton> tabs = segments(i -> {
            boolean pendingTab = i == 0;
            content.getChildren().setAll(pendingTab ? body : decided);
            actions.setVisible(pendingTab && queue.getSelectionModel().getSelectedItem() != null);
            actions.setManaged(actions.isVisible());
            search.setDisable(!pendingTab);
            if (!pendingTab) loadDecisions(decided, i == 1 ? "APPROVE_PENDING_TREE" : "REJECT_PENDING_TREE");
        }, "Pending  " + pendingTrees.size(), "Approved", "Rejected");
        HBox toolbar = new HBox(10, withGlass(search), RegistryStyle.spacer(), new HBox(0, tabs.toArray(new Node[0])));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        if (pendingTrees.isEmpty()) {
            body.getChildren().setAll(Skeleton.empty(Skeleton.ICON_INBOX, "All caught up",
                    "New ranger submissions appear here for review before they join the inventory and the 3D map.", null));
            HBox.setHgrow(body.getChildren().get(0), Priority.ALWAYS);
        }
        VBox center = new VBox(14, toolbar, content);
        VBox.setVgrow(content, Priority.ALWAYS);
        center.setPadding(new Insets(20, 24, 20, 24));
        page.setCenter(center);
        page.setBottom(actions);
        actions.setVisible(false);
        actions.setManaged(false);

        // Submitted dates come from the ranger's SUBMIT_PENDING_TREE log entry (species + location + owner).
        io.execute(() -> {
            try {
                Map<String, String> found = new HashMap<>();
                for (LogEntry e : readLog()) {
                    if (!"SUBMIT_PENDING_TREE".equals(e.action())) continue;
                    found.put(e.user() + "|" + e.details(), e.time());
                }
                Platform.runLater(() -> {
                    for (Tree t : pendingTrees) {
                        String ts = found.get(t.ownerUsername + "|Species: " + t.species + ", Location: " + t.location);
                        submittedAt.put(key(t), ts == null ? "submitted date not recorded" : "submitted " + ts.substring(0, Math.min(16, ts.length())));
                    }
                    queue.refresh();
                    Tree sel = queue.getSelectionModel().getSelectedItem();
                    if (sel != null) showPendingDetail(sel, detail, actions, submittedAt);
                });
            } catch (Exception ignored) {
            }
        });
        queue.getSelectionModel().selectFirst();
        return page;
    }

    private static String key(Tree t) {
        return t.id + "|" + t.ownerUsername;
    }

    private void showPendingDetail(Tree t, StackPane detail, HBox actions, Map<String, String> submittedAt) {
        if (t == null) {
            detail.getChildren().setAll(Skeleton.empty(Skeleton.ICON_INBOX, "Select a submission", "Pick a submission on the left to review it.", null));
            actions.setVisible(false);
            actions.setManaged(false);
            return;
        }
        VBox card = new VBox(14);
        card.getStyleClass().add("detail-card");
        if (t.imageBytes != null && t.imageBytes.length > 0) {
            ImageView photo = new ImageView(UIUtils.bytesToImage(t.imageBytes));
            photo.setPreserveRatio(true);
            photo.setFitHeight(200);
            card.getChildren().add(photo);
        } else {
            Label none = new Label("No photo attached: check the measurements and notes carefully.");
            none.getStyleClass().add("muted");
            card.getChildren().add(none);
        }
        Label idPill = new Label("#" + t.id);
        idPill.getStyleClass().add("id-pill");
        card.getChildren().addAll(RegistryStyle.heading(t.species), new HBox(6, pill(t.status, TreeDialogs.statusColor(t.status)), idPill),
                new Separator(),
                facts(new String[][]{
                        {"Submitted by", displayName.apply(t.ownerUsername) + " (@" + t.ownerUsername + ")"},
                        {"Submitted", Optional.ofNullable(submittedAt.get(key(t))).map(s -> s.replace("submitted ", "")).orElse("loading…")},
                        {"Height", t.height + " m"},
                        {"Trunk diameter", t.diameter + " cm"},
                        {"Planted", t.plantedDate},
                        {"Location", t.location}}),
                RegistryStyle.caption("FIELD NOTES"),
                new Label(t.notes == null || t.notes.isBlank() ? "No field notes." : t.notes));
        ScrollPane scroll = new ScrollPane(card);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page-scroll");
        detail.getChildren().setAll(scroll);

        Label hint = new Label("Approving adds this tree to the inventory and the 3D Forest Map with the same ID.");
        hint.getStyleClass().add("muted");
        Button reject = new Button("Reject…");
        reject.getStyleClass().add("danger");
        reject.setOnAction(e -> {
            if (confirm(detail, "Reject " + t.species + " #" + t.id + "?",
                    "The submission is removed from the queue and " + t.ownerUsername + " will see it as rejected in My submissions.", "Reject", true)) {
                act("Rejecting…", () -> trees.rejectPendingTree(t.id, token), reopenPending);
            }
        });
        Button approve = RegistryStyle.primary("Approve…", () -> {
            if (confirm(detail, "Approve " + t.species + " #" + t.id + "?",
                    "It joins the active inventory and appears on the 3D Forest Map.", "Approve", false)) {
                act("Approving…", () -> trees.approvePendingTree(t.id, token), () -> { refreshInventory.run(); reopenPending.run(); });
            }
        });
        approve.setText("Approve");
        approve.setDefaultButton(false);
        actions.getChildren().setAll(hint, RegistryStyle.spacer(), reject, approve);
        actions.setVisible(true);
        actions.setManaged(true);
    }

    private void loadDecisions(StackPane target, String action) {
        target.getChildren().setAll(Skeleton.rows(5));
        io.execute(() -> {
            try {
                List<LogEntry> log = readLog();
                List<LogEntry> rows = new ArrayList<>();
                for (LogEntry e : log) if (action.equals(e.action())) rows.add(e);
                Collections.reverse(rows);
                Platform.runLater(() -> {
                    if (rows.isEmpty()) {
                        target.getChildren().setAll(Skeleton.empty(Skeleton.ICON_INBOX,
                                action.startsWith("APPROVE") ? "No approvals yet" : "No rejections yet",
                                "Decisions are listed here from the server activity log.", null));
                        return;
                    }
                    TableView<LogEntry> table = logTable(rows, false);
                    target.getChildren().setAll(table);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> target.getChildren().setAll(Skeleton.error("The decision history couldn't be loaded.", () -> loadDecisions(target, action))));
            }
        });
    }

    // =====================================================================================================
    // Manage Users
    // =====================================================================================================

    /** One row of the user table, assembled from usernames, profiles, online status and the activity log. */
    static final class UserRow {
        final String username, displayName, role, lastSignIn;
        final boolean online;
        final long treesRecorded;
        UserRow(String username, String displayName, String role, boolean online, String lastSignIn, long treesRecorded) {
            this.username = username; this.displayName = displayName; this.role = role;
            this.online = online; this.lastSignIn = lastSignIn; this.treesRecorded = treesRecorded;
        }
    }

    Node users() {
        Label subtitle = new Label("Loading accounts…");
        BorderPane page = page("Manage Users", subtitle);
        StackPane content = new StackPane(Skeleton.rows(6));
        VBox center = new VBox(14);
        center.setPadding(new Insets(20, 24, 20, 24));
        page.setCenter(center);
        Button refresh = RegistryStyle.secondary("Refresh", () -> loadUsers(page, subtitle, content, center));
        ((HBox) page.getTop()).getChildren().add(refresh);
        center.getChildren().setAll(content);
        loadUsers(page, subtitle, content, center);
        return page;
    }

    private void loadUsers(BorderPane page, Label subtitle, StackPane content, VBox center) {
        center.getChildren().setAll(content);
        content.getChildren().setAll(Skeleton.rows(6));
        io.execute(() -> {
            try {
                List<String> names = users.getAllUsernames(token);
                Set<String> on = new HashSet<>(chat.getOnlineUsers(token));
                Map<String, String> lastLogin = new HashMap<>();
                try { for (LogEntry e : readLog()) if ("LOGIN".equals(e.action())) lastLogin.put(e.user(), e.time()); } catch (Exception ignored) {}
                List<UserRow> rows = new ArrayList<>();
                for (String u : names) {
                    String dn = u;
                    try { UserProfile p = users.getProfile(token, u); if (p != null && p.displayName != null && !p.displayName.isBlank()) dn = p.displayName; } catch (Exception ignored) {}
                    rows.add(new UserRow(u, dn, "admin".equals(u) ? "Administrator" : "Ranger", on.contains(u),
                            lastLogin.getOrDefault(u, ""), treesRecordedBy.apply(u)));
                }
                Platform.runLater(() -> showUsers(page, subtitle, center, rows));
            } catch (Exception ex) {
                Platform.runLater(() -> center.getChildren().setAll(Skeleton.error("The account list couldn't be loaded: " + ex.getMessage(),
                        () -> loadUsers(page, subtitle, content, center))));
            }
        });
    }

    private void showUsers(BorderPane page, Label subtitle, VBox center, List<UserRow> rows) {
        long admins = rows.stream().filter(r -> r.role.equals("Administrator")).count();
        long online = rows.stream().filter(r -> r.online).count();
        subtitle.setText(rows.size() + " accounts · " + admins + (admins == 1 ? " administrator" : " administrators") + " · " + online + " online now");
        ObservableList<UserRow> data = FXCollections.observableArrayList(rows);
        FilteredList<UserRow> filtered = new FilteredList<>(data, r -> true);
        SortedList<UserRow> sorted = new SortedList<>(filtered);
        TableView<UserRow> table = new TableView<>(sorted);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.getStyleClass().add("inventory-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No accounts match these filters."));

        TableColumn<UserRow, UserRow> person = new TableColumn<>("User");
        person.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        person.setComparator(Comparator.comparing(r -> r.displayName.toLowerCase(Locale.ROOT)));
        person.setPrefWidth(230);
        person.setMinWidth(180);
        person.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(UserRow r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) { setGraphic(null); return; }
                Label n = new Label(r.displayName); n.setStyle("-fx-font-weight: bold;");
                Label u = new Label("@" + r.username); u.getStyleClass().add("muted");
                HBox row = new HBox(10, initialAvatar(r.displayName, 30), new VBox(1, n, u));
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });
        TableColumn<UserRow, String> role = new TableColumn<>("Role");
        role.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().role));
        role.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String r, boolean empty) {
                super.updateItem(r, empty);
                setGraphic(empty || r == null ? null : pill(r, r.equals("Administrator") ? "#6B5B95" : "#20865B"));
                setText(null);
            }
        });
        TableColumn<UserRow, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().online ? "Online" : "Offline"));
        status.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setGraphic(empty || s == null ? null : pill(s, s.equals("Online") ? "#2FA36F" : "#8A9890"));
                setText(null);
            }
        });
        TableColumn<UserRow, String> last = new TableColumn<>("Last sign-in");
        last.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().lastSignIn.isEmpty() ? "Never" : c.getValue().lastSignIn.substring(0, Math.min(16, c.getValue().lastSignIn.length()))));
        TableColumn<UserRow, Number> recorded = new TableColumn<>("Trees recorded");
        recorded.setCellValueFactory(c -> new javafx.beans.property.SimpleLongProperty(c.getValue().treesRecorded));
        table.getColumns().addAll(List.of(person, role, status, last, recorded));
        table.getSortOrder().add(person);

        TextField search = searchField("Search name or username");
        ComboBox<String> roleFilter = new ComboBox<>(FXCollections.observableArrayList("All roles", "Administrators", "Rangers"));
        roleFilter.setValue("All roles");
        roleFilter.getStyleClass().add("sort-box");
        ComboBox<String> statusFilter = new ComboBox<>(FXCollections.observableArrayList("Any status", "Online", "Offline"));
        statusFilter.setValue("Any status");
        statusFilter.getStyleClass().add("sort-box");
        Runnable apply = () -> {
            String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(r -> (q.isEmpty() || (r.username + " " + r.displayName).toLowerCase(Locale.ROOT).contains(q))
                    && (roleFilter.getValue().equals("All roles") || (roleFilter.getValue().equals("Administrators") == r.role.equals("Administrator")))
                    && (statusFilter.getValue().equals("Any status") || (statusFilter.getValue().equals("Online") == r.online)));
        };
        search.textProperty().addListener((o, a, b) -> apply.run());
        roleFilter.valueProperty().addListener((o, a, b) -> apply.run());
        statusFilter.valueProperty().addListener((o, a, b) -> apply.run());
        HBox toolbar = new HBox(10, withGlass(search), roleFilter, statusFilter);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox detail = detailCard();
        Runnable reload = () -> loadUsers(page, subtitle, new StackPane(), center);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, r) -> showUser(r, detail, reload));
        HBox body = new HBox(16, table, detail);
        HBox.setHgrow(table, Priority.ALWAYS);
        VBox.setVgrow(body, Priority.ALWAYS);
        center.getChildren().setAll(toolbar, body);
        table.getSelectionModel().selectFirst();
    }

    private void showUser(UserRow r, VBox card, Runnable reload) {
        card.getChildren().clear();
        if (r == null) {
            card.getChildren().add(Skeleton.empty(Skeleton.ICON_USERS, "Select an account", "Choose a user to see details and actions.", null));
            return;
        }
        Label name = new Label(r.displayName); name.getStyleClass().add("section-title");
        Label handle = new Label("@" + r.username); handle.getStyleClass().add("muted");
        HBox head = new HBox(12, initialAvatar(r.displayName, 52), new VBox(2, name, handle));
        head.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(head,
                new HBox(6, pill(r.role, r.role.equals("Administrator") ? "#6B5B95" : "#20865B"), pill(r.online ? "Online" : "Offline", r.online ? "#2FA36F" : "#8A9890")),
                new Separator(),
                facts(new String[][]{
                        {"Role", r.role.equals("Administrator") ? "Administrator: reviews submissions and manages accounts" : "Ranger: records trees for review"},
                        {"Last sign-in", r.lastSignIn.isEmpty() ? "Never signed in" : r.lastSignIn},
                        {"Trees recorded", String.valueOf(r.treesRecorded)}}));
        Button message = RegistryStyle.secondary("Send message", () -> openMessagesWith.accept(r.username));
        message.setDisable(r.username.equals(me));
        message.setMaxWidth(Double.MAX_VALUE);
        Button password = RegistryStyle.secondary("Change password…", () -> changePassword(card, r));
        password.setMaxWidth(Double.MAX_VALUE);
        Button delete = new Button("Delete account…");
        delete.getStyleClass().add("danger");
        delete.setMaxWidth(Double.MAX_VALUE);
        boolean protectedAccount = r.username.equals("admin") || r.username.equals(me);
        delete.setDisable(protectedAccount);
        if (protectedAccount) delete.setTooltip(new Tooltip("The primary administrator account can't be deleted."));
        delete.setOnAction(e -> deleteAccount(card, r, reload));
        Label danger = RegistryStyle.caption("DANGER ZONE");
        card.getChildren().addAll(new Separator(), message, password, danger, delete);
    }

    private void changePassword(Node owner, UserRow r) {
        Dialog<String> d = new Dialog<>();
        if (owner.getScene() != null) d.initOwner(owner.getScene().getWindow());
        d.setTitle("Change password");
        d.setHeaderText("Set a new password for " + r.displayName + " (@" + r.username + ")");
        PasswordField p1 = new PasswordField(); p1.setPromptText("New password (at least 8 characters)");
        PasswordField p2 = new PasswordField(); p2.setPromptText("Repeat the new password");
        Label err = new Label(); err.setStyle("-fx-text-fill: #9D2235;");
        VBox box = new VBox(10, p1, p2, err);
        box.setPrefWidth(360);
        d.getDialogPane().setContent(box);
        ButtonType save = new ButtonType("Change password", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, save);
        Button saveBtn = (Button) d.getDialogPane().lookupButton(save);
        saveBtn.setDisable(true);
        Runnable check = () -> {
            String a = p1.getText(), b = p2.getText();
            String problem = a.length() < 8 ? (a.isEmpty() ? "" : "Use at least 8 characters.")
                    : !a.equals(b) ? (b.isEmpty() ? "" : "The passwords don't match.") : null;
            err.setText(problem == null ? "" : problem);
            saveBtn.setDisable(problem != null);
        };
        p1.textProperty().addListener((o, x, y) -> check.run());
        p2.textProperty().addListener((o, x, y) -> check.run());
        d.setResultConverter(bt -> bt == save ? p1.getText() : null);
        Platform.runLater(p1::requestFocus);
        d.showAndWait().ifPresent(pw -> act("Changing password…", () -> users.adminSetPassword(token, r.username, pw), null));
    }

    private void deleteAccount(Node owner, UserRow r, Runnable reload) {
        TextInputDialog d = new TextInputDialog();
        if (owner.getScene() != null) d.initOwner(owner.getScene().getWindow());
        d.setTitle("Delete account");
        d.setHeaderText("Delete @" + r.username + "? This can't be undone.");
        d.setContentText("Type the username to confirm:");
        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
        ok.setText("Delete account");
        ok.setDisable(true);
        d.getEditor().textProperty().addListener((o, a, t) -> ok.setDisable(!r.username.equals(t == null ? "" : t.trim())));
        d.showAndWait().filter(t -> r.username.equals(t.trim()))
                .ifPresent(t -> act("Deleting account…", () -> users.deleteUser(token, r.username), reload));
    }

    private static Node initialAvatar(String name, double size) {
        String[] palette = {"#20865B", "#457B9D", "#BC6C25", "#6B5B95", "#2F6F6A", "#8A5A44"};
        Circle c = new Circle(size / 2, Color.web(palette[Math.floorMod(name.hashCode(), palette.length)]));
        Label l = new Label(name.isBlank() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT));
        l.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: " + (size * 0.42) + "px;");
        StackPane s = new StackPane(c, l);
        s.setMinSize(size, size);
        s.setMaxSize(size, size);
        return s;
    }

    // =====================================================================================================
    // Archive
    // =====================================================================================================

    Node archive() {
        Label subtitle = new Label("Loading archived records…");
        BorderPane page = page("Archive", subtitle);
        VBox center = new VBox(14);
        center.setPadding(new Insets(20, 24, 20, 24));
        page.setCenter(center);
        Button refresh = RegistryStyle.secondary("Refresh", () -> loadArchive(subtitle, center));
        ((HBox) page.getTop()).getChildren().add(refresh);
        loadArchive(subtitle, center);
        return page;
    }

    private void loadArchive(Label subtitle, VBox center) {
        center.getChildren().setAll(Skeleton.rows(6));
        io.execute(() -> {
            try {
                List<Tree> deleted = trees.getDeletedTrees(token);
                Platform.runLater(() -> showArchive(subtitle, center, deleted));
            } catch (Exception ex) {
                Platform.runLater(() -> center.getChildren().setAll(Skeleton.error("The archive couldn't be loaded: " + ex.getMessage(), () -> loadArchive(subtitle, center))));
            }
        });
    }

    private void showArchive(Label subtitle, VBox center, List<Tree> deleted) {
        subtitle.setText(deleted.isEmpty() ? "No archived records." : deleted.size() + (deleted.size() == 1 ? " record removed from the inventory" : " records removed from the inventory"));
        if (deleted.isEmpty()) {
            center.getChildren().setAll(Skeleton.empty(Skeleton.ICON_ARCHIVE, "The archive is empty",
                    "When a tree is deleted from the inventory, its record and photo are kept here so they can be restored.", null));
            return;
        }
        ObservableList<Tree> data = FXCollections.observableArrayList(deleted);
        FilteredList<Tree> filtered = new FilteredList<>(data, t -> true);
        SortedList<Tree> sorted = new SortedList<>(filtered);
        TableView<Tree> table = new TableView<>(sorted);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.getStyleClass().add("inventory-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No archived records match."));
        TableColumn<Tree, Number> id = new TableColumn<>("ID");
        id.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().id));
        id.setMaxWidth(90);
        TableColumn<Tree, String> species = new TableColumn<>("Species");
        species.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().species));
        TableColumn<Tree, String> status = new TableColumn<>("Last status");
        status.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status));
        status.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setGraphic(empty || s == null ? null : pill(s, TreeDialogs.statusColor(s)));
                setText(null);
            }
        });
        TableColumn<Tree, String> planted = new TableColumn<>("Planted");
        planted.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().plantedDate));
        TableColumn<Tree, String> owner = new TableColumn<>("Recorded by");
        owner.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().ownerUsername));
        table.getColumns().addAll(List.of(id, species, status, planted, owner));

        TextField search = searchField("Search species, ID or ranger");
        ComboBox<String> statusFilter = new ComboBox<>(FXCollections.observableArrayList("All statuses", "Healthy", "Monitoring", "At Risk", "Destroyed"));
        statusFilter.setValue("All statuses");
        statusFilter.getStyleClass().add("sort-box");
        Runnable apply = () -> {
            String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
            String st = statusFilter.getValue();
            filtered.setPredicate(t -> (q.isEmpty() || (t.species + " " + t.id + " " + t.ownerUsername).toLowerCase(Locale.ROOT).contains(q))
                    && (st.equals("All statuses") || st.equalsIgnoreCase(t.status)));
        };
        search.textProperty().addListener((o, a, b) -> apply.run());
        statusFilter.valueProperty().addListener((o, a, b) -> apply.run());
        HBox toolbar = new HBox(10, withGlass(search), statusFilter);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox detail = detailCard();
        table.getSelectionModel().selectedItemProperty().addListener((o, a, t) -> showArchived(t, detail, data));
        HBox body = new HBox(16, table, detail);
        HBox.setHgrow(table, Priority.ALWAYS);
        VBox.setVgrow(body, Priority.ALWAYS);
        center.getChildren().setAll(toolbar, body);
        table.getSelectionModel().selectFirst();
    }

    private void showArchived(Tree t, VBox card, ObservableList<Tree> data) {
        card.getChildren().clear();
        if (t == null) {
            card.getChildren().add(Skeleton.empty(Skeleton.ICON_ARCHIVE, "Select a record", "Choose an archived tree to see details.", null));
            return;
        }
        if (t.imageBytes != null && t.imageBytes.length > 0) {
            ImageView photo = new ImageView(UIUtils.bytesToImage(t.imageBytes));
            photo.setPreserveRatio(true);
            photo.setFitWidth(320);
            photo.setFitHeight(180);
            card.getChildren().add(photo);
        }
        Label name = new Label(t.species + "  #" + t.id);
        name.getStyleClass().add("section-title");
        VBox history = new VBox(4);
        Label loading = new Label("Loading history…");
        loading.getStyleClass().add("muted");
        history.getChildren().add(loading);
        card.getChildren().addAll(name, pill(t.status, TreeDialogs.statusColor(t.status)), new Separator(),
                facts(new String[][]{{"Height", t.height + " m"}, {"Trunk diameter", t.diameter + " cm"}, {"Planted", t.plantedDate},
                        {"Location", t.location}, {"Recorded by", t.ownerUsername}, {"Notes", t.notes}}),
                RegistryStyle.caption("HISTORY"), history);
        io.execute(() -> {
            try {
                List<String> lines = trees.getTreeHistory(t.id, token);
                Platform.runLater(() -> {
                    history.getChildren().clear();
                    if (lines.isEmpty()) { Label none = new Label("No recorded activity."); none.getStyleClass().add("muted"); history.getChildren().add(none); }
                    for (String l : lines) {
                        String[] p = l.split("\\s\\|\\s", 3);
                        Label row = new Label(p.length == 3 ? p[2] + "  ·  " + p[0] + " · " + p[1] : l);
                        row.setWrapText(true);
                        row.getStyleClass().add("muted");
                        history.getChildren().add(row);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> history.getChildren().setAll(new Label("History unavailable.")));
            }
        });
        Button restore = RegistryStyle.primary("Restore to inventory…", () -> {
            if (confirm(card, "Restore " + t.species + " #" + t.id + "?", "It returns to the active inventory and the 3D Forest Map with its original ID and photo.", "Restore", false)) {
                act("Restoring…", () -> trees.restoreDeletedTree(t.id, token), () -> { data.remove(t); refreshInventory.run(); });
            }
        });
        restore.setMaxWidth(Double.MAX_VALUE);
        Button purge = new Button("Delete permanently…");
        purge.getStyleClass().add("danger");
        purge.setMaxWidth(Double.MAX_VALUE);
        purge.setOnAction(e -> {
            if (confirm(card, "Permanently delete " + t.species + " #" + t.id + "?",
                    "The archived record and its photo are erased. This can't be undone.", "Delete permanently", true)) {
                act("Deleting…", () -> trees.purgeDeletedTree(t.id, token), () -> data.remove(t));
            }
        });
        card.getChildren().addAll(new Separator(), restore, RegistryStyle.caption("DANGER ZONE"), purge);
    }

    // =====================================================================================================
    // Ranger Access
    // =====================================================================================================

    Node rangerAccess() {
        Label subtitle = new Label("Control whether rangers can sign in to this server.");
        BorderPane page = page("Ranger Access", subtitle);
        VBox center = new VBox(16);
        center.setPadding(new Insets(20, 24, 20, 24));
        center.setMaxWidth(980);
        ScrollPane scroll = new ScrollPane(center);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page-scroll");
        page.setCenter(scroll);
        Button refresh = RegistryStyle.secondary("Refresh", () -> loadAccess(center));
        ((HBox) page.getTop()).getChildren().add(refresh);
        loadAccess(center);
        return page;
    }

    private void loadAccess(VBox center) {
        center.getChildren().setAll(Skeleton.rows(3));
        io.execute(() -> {
            try {
                boolean open = "STATUS_ON".equals(admin.getServerStatus());
                List<String> on = chat.getOnlineUsers(token);
                LogEntry lastChange = null;
                List<LogEntry> recentLogins = new ArrayList<>();
                try {
                    for (LogEntry e : readLog()) {
                        if ("SERVER_CONTROL".equals(e.action())) lastChange = e;
                        if ("LOGIN".equals(e.action()) && !"admin".equals(e.user())) recentLogins.add(e);
                    }
                } catch (Exception ignored) {}
                LogEntry changed = lastChange;
                Collections.reverse(recentLogins);
                List<LogEntry> recent = recentLogins.subList(0, Math.min(6, recentLogins.size()));
                Platform.runLater(() -> showAccess(center, open, on, changed, recent));
            } catch (Exception ex) {
                Platform.runLater(() -> center.getChildren().setAll(Skeleton.error("Couldn't read the server's access setting: " + ex.getMessage(), () -> loadAccess(center))));
            }
        });
    }

    private void showAccess(VBox center, boolean open, List<String> online, LogEntry lastChange, List<LogEntry> recent) {
        String accent = open ? "#20865B" : "#BC6C25";
        Circle light = new Circle(9, Color.web(accent));
        light.setEffect(new javafx.scene.effect.DropShadow(10, Color.web(accent, 0.6)));
        Label state = new Label(open ? "Ranger sign-in is open" : "Ranger sign-in is paused");
        state.getStyleClass().add("access-state");
        state.setStyle("-fx-text-fill: " + accent + ";");
        Label explain = new Label(open
                ? "Rangers can sign in, submit trees and send messages. Administrators can always sign in."
                : "Rangers can't sign in until you enable access again. Rangers already signed in keep their session. Administrators can always sign in.");
        explain.setWrapText(true);
        explain.getStyleClass().add("muted");
        Label since = new Label(lastChange == null ? "No change recorded in the activity log."
                : "Last changed " + lastChange.time() + " by " + lastChange.user() + ".");
        since.getStyleClass().add("muted");
        Button toggle;
        if (open) {
            toggle = new Button("Pause ranger sign-in…");
            toggle.getStyleClass().add("danger");
            toggle.setOnAction(e -> {
                if (confirm(center, "Pause ranger sign-in?", "Rangers won't be able to sign in until you enable it again. Signed-in rangers stay connected.", "Pause sign-in", true)) {
                    act("Pausing ranger sign-in…", () -> { String r = admin.disableServerLogin(token); return r != null && r.startsWith("OK") ? "Ranger sign-in paused." : r; }, () -> loadAccess(center));
                }
            });
        } else {
            toggle = RegistryStyle.primary("Enable ranger sign-in…", () -> {
                if (confirm(center, "Enable ranger sign-in?", "Rangers will be able to sign in to this server again.", "Enable sign-in", false)) {
                    act("Enabling ranger sign-in…", () -> { String r = admin.enableServerLogin(token); return r != null && r.startsWith("OK") ? "Ranger sign-in enabled." : r; }, () -> loadAccess(center));
                }
            });
        }
        VBox text = new VBox(6, state, explain, since);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox hero = new HBox(16, light, text, toggle);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("detail-card");
        hero.setStyle("-fx-border-color: #dfe5e0 #dfe5e0 #dfe5e0 " + accent + "; -fx-border-width: 1 1 1 5;");

        long rangersOnline = online.stream().filter(u -> !"admin".equals(u)).count();
        VBox onlineCard = new VBox(8);
        onlineCard.getStyleClass().add("detail-card");
        Label oc = new Label("Signed in now  ·  " + rangersOnline + (rangersOnline == 1 ? " ranger" : " rangers"));
        oc.getStyleClass().add("chart-title");
        onlineCard.getChildren().add(oc);
        if (online.isEmpty()) {
            Label none = new Label("Nobody else is signed in.");
            none.getStyleClass().add("muted");
            onlineCard.getChildren().add(none);
        }
        for (String u : online) {
            HBox row = new HBox(10, initialAvatar(displayName.apply(u), 26), new Label(displayName.apply(u) + ("admin".equals(u) ? "  (administrator)" : "")));
            row.setAlignment(Pos.CENTER_LEFT);
            onlineCard.getChildren().add(row);
        }
        VBox recentCard = new VBox(8);
        recentCard.getStyleClass().add("detail-card");
        Label rc = new Label("Recent ranger sign-ins");
        rc.getStyleClass().add("chart-title");
        recentCard.getChildren().add(rc);
        if (recent.isEmpty()) { Label none = new Label("No ranger sign-ins recorded yet."); none.getStyleClass().add("muted"); recentCard.getChildren().add(none); }
        for (LogEntry e : recent) {
            Label l = new Label(displayName.apply(e.user()) + "  ·  " + e.time());
            recentCard.getChildren().add(l);
        }
        HBox cards = new HBox(16, onlineCard, recentCard);
        HBox.setHgrow(onlineCard, Priority.ALWAYS);
        HBox.setHgrow(recentCard, Priority.ALWAYS);
        center.getChildren().setAll(hero, cards);
    }

    // =====================================================================================================
    // System States (activity log)
    // =====================================================================================================

    Node systemLog() {
        Label subtitle = new Label("Every sign-in, submission, review and change recorded by the server.");
        BorderPane page = page("System States", subtitle);
        VBox center = new VBox(14);
        center.setPadding(new Insets(20, 24, 20, 24));
        page.setCenter(center);
        Button refresh = RegistryStyle.secondary("Refresh", () -> loadLog(center));
        ((HBox) page.getTop()).getChildren().add(refresh);
        loadLog(center);
        return page;
    }

    private void loadLog(VBox center) {
        center.getChildren().setAll(Skeleton.rows(8));
        io.execute(() -> {
            try {
                List<LogEntry> rows = readLog();
                Collections.reverse(rows);
                Platform.runLater(() -> {
                    if (rows.isEmpty()) {
                        center.getChildren().setAll(Skeleton.empty(Skeleton.ICON_LOG, "No activity yet", "Server events appear here as people use the registry.", null));
                        return;
                    }
                    TableView<LogEntry> table = logTable(rows, true);
                    FilteredList<LogEntry> filtered = (FilteredList<LogEntry>) ((SortedList<LogEntry>) table.getItems()).getSource();
                    TextField search = searchField("Search user, action or details");
                    List<String> actions = new ArrayList<>(new TreeSet<>(rows.stream().map(LogEntry::action).toList()));
                    actions.add(0, "All actions");
                    ComboBox<String> action = new ComboBox<>(FXCollections.observableArrayList(actions));
                    action.setValue("All actions");
                    action.getStyleClass().add("sort-box");
                    Label count = new Label(rows.size() + " events");
                    count.getStyleClass().add("muted");
                    Runnable apply = () -> {
                        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
                        filtered.setPredicate(e -> (q.isEmpty() || (e.user() + " " + e.action() + " " + e.details()).toLowerCase(Locale.ROOT).contains(q))
                                && (action.getValue().equals("All actions") || action.getValue().equals(e.action())));
                        count.setText(filtered.size() + " of " + rows.size() + " events");
                    };
                    search.textProperty().addListener((o, a, b) -> apply.run());
                    action.valueProperty().addListener((o, a, b) -> apply.run());
                    HBox toolbar = new HBox(10, withGlass(search), action, RegistryStyle.spacer(), count);
                    toolbar.setAlignment(Pos.CENTER_LEFT);
                    VBox.setVgrow(table, Priority.ALWAYS);
                    center.getChildren().setAll(toolbar, table);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> center.getChildren().setAll(Skeleton.error("The activity log couldn't be loaded: " + ex.getMessage(), () -> loadLog(center))));
            }
        });
    }

    private TableView<LogEntry> logTable(List<LogEntry> rows, boolean showAction) {
        FilteredList<LogEntry> filtered = new FilteredList<>(FXCollections.observableArrayList(rows), e -> true);
        SortedList<LogEntry> sorted = new SortedList<>(filtered);
        TableView<LogEntry> table = new TableView<>(sorted);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.getStyleClass().add("inventory-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<LogEntry, String> when = new TableColumn<>("When");
        when.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().time()));
        when.setMaxWidth(190);
        when.setMinWidth(150);
        TableColumn<LogEntry, String> who = new TableColumn<>("User");
        who.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().user()));
        who.setMaxWidth(160);
        TableColumn<LogEntry, String> what = new TableColumn<>("Action");
        what.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().action()));
        what.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) { setGraphic(null); return; }
                Label l = new Label(a.replace('_', ' ').toLowerCase(Locale.ROOT));
                l.getStyleClass().add("id-pill");
                setGraphic(l);
                setText(null);
            }
        });
        what.setMaxWidth(220);
        TableColumn<LogEntry, String> details = new TableColumn<>("Details");
        details.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().details()));
        table.getColumns().addAll(showAction ? List.of(when, who, what, details) : List.of(when, who, details));
        table.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.C && e.isShortcutDown() && table.getSelectionModel().getSelectedItem() != null) {
                LogEntry s = table.getSelectionModel().getSelectedItem();
                javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent();
                c.putString("[" + s.time() + "] " + s.user() + " " + s.action() + ": " + s.details());
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
            }
        });
        return table;
    }
}
