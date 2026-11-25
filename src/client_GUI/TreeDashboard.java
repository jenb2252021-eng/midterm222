package client_GUI;

import Model.Tree;
import Model.UserProfile;
import Server.*;


import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.ExecutorService;

import storage.ImageStorage;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Earthified Dashboard for SDG 15 - The Lorax.
 * Features a nature-inspired palette and botanical field journal aesthetics.
 */
public class TreeDashboard extends Stage {

    private final TreeService treeService;
    private final UserService userService;
    private final AdminService adminService;
    private final ChatService chatService;
    private final boolean isAdmin;
    private final String username;
    private final String sessionToken;
    private final List<Tree> cachedTrees = new ArrayList<>();

    // UI Components
    private GridPane cardContainer;
    private final Label inventoryCount = new Label();
    private Button selectedNav;
    private Button registryButton;
    private final Label onlineUsers = new Label();
    private final Button reconnect = new Button("Reconnect");
    private final Label connection = new Label("Connecting.");
    private final List<Timeline> timers = new ArrayList<>();
    private BorderPane statsWrapper;
    private TextArea logArea;
    private TextField txtSearch;
    private ComboBox<String> statusFilter;
    private final ContextMenu searchSuggestionsPopup = new ContextMenu();
    private final List<Tree> currentSearchSuggestions = new ArrayList<>();
    private int highlightedSuggestionIndex = -1;

    // Admin/Notification Components
    private Label lblServerStatus;
    private Timeline inventoryRefreshTimer;
    private Label pendingBadgeLabel;
    private Label chatBadgeLabel;
    private Timeline activeUsersTimer;
    private int activeUsersCount = 0;
    private final BorderPane workspace = new BorderPane();
    private SplitPane messagesPane;
    private final java.util.Map<String, String> displayNameCache = new java.util.HashMap<>();

    private final ExecutorService serverControlExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ServerControl");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService dataExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TreeDataFetch");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean fetchInProgress = new AtomicBoolean(false);

    // --- EARTHIFIED UI PALETTE ---
    private final String SDG_GREEN = "#20865B";    // Deep Forest Green
    private final String PANEL_DARK = "#17251B";   // Bark Brown
    private final String EARTH_CREAM = "#F5F7F5";  // Soft Sand / Recycled Paper
    private final String DELETE_RED = "#9D2235";   // Dried Clay Red
    private final String RIVER_BLUE = "#457B9D";   // River Water Blue
    private final Image editActionIcon = loadUiIcon("/res/icons/edit.png", "src/res/icons/edit.png");
    private final Image deleteActionIcon = loadUiIcon("/res/icons/delete.png", "src/res/icons/delete.png");

    public TreeDashboard(Registry registry, String username, String sessionToken) {
        this.username = username;
        this.sessionToken = sessionToken;
        this.isAdmin = "admin".equals(username);

        try {
            this.treeService = (TreeService) registry.lookup("TreeService");
            this.userService = (UserService) registry.lookup("UserService");
            this.adminService = (AdminService) registry.lookup("AdminService");
            this.chatService = (ChatService) registry.lookup("ChatService");
        } catch (Exception e) {
            throw new RuntimeException("Failed to lookup RMI services: " + e.getMessage(), e);
        }

        setTitle("The Lorax · Forest Registry · " + (isAdmin ? "Admin" : "Ranger"));
        setMinWidth(1000);
        setMinHeight(700);

        BorderPane root = workspace;
        root.setStyle("-fx-background-color: " + EARTH_CREAM + ";");

        initSearchField();
        initStatusFilter();
        root.setLeft(sidebar(root));
        showRegistry();
        Scene scene = RegistryStyle.scene(root, 1280, 800);
        setOnHidden(e -> {
            timers.forEach(Timeline::stop);
            inventoryRefreshTimer.stop();
            if (activeUsersTimer != null) activeUsersTimer.stop();
        });
        setScene(scene);

        setOnCloseRequest(_ -> {
            try { userService.signOut(sessionToken); } catch (Exception ignored) {}
            Platform.exit();
            System.exit(0);
        });

        loadInitialData();
        startInventoryAutoRefresh();
        startNotificationTimers();
    }

    private void initSearchField() {
        txtSearch = new TextField();
        txtSearch.setPrefWidth(300);
        txtSearch.setPrefHeight(35);
        txtSearch.setPromptText("Search trees, species, or ID");
        // Sage Green
        String LEAF_LIGHT = "#A3B18A";
        txtSearch.setStyle("-fx-background-radius: 15; -fx-border-color: " + LEAF_LIGHT + "; -fx-border-radius: 15; -fx-background-color: white;");
        searchSuggestionsPopup.setAutoHide(true);
        searchSuggestionsPopup.setHideOnEscape(true);
        txtSearch.textProperty().addListener((obs, oldText, newText) -> {
            performSearch();
            refreshSearchSuggestions();
        });
        txtSearch.focusedProperty().addListener((obs, oldFocused, focused) -> {
            if (focused) {
                refreshSearchSuggestions();
            } else {
                searchSuggestionsPopup.hide();
            }
        });
        txtSearch.setOnKeyPressed(e -> {
            if (!searchSuggestionsPopup.isShowing() || currentSearchSuggestions.isEmpty()) {
                return;
            }
            if (e.getCode() == KeyCode.DOWN) {
                moveSuggestionHighlight(1);
                e.consume();
            } else if (e.getCode() == KeyCode.UP) {
                moveSuggestionHighlight(-1);
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                if (highlightedSuggestionIndex >= 0 && highlightedSuggestionIndex < currentSearchSuggestions.size()) {
                    applySuggestion(currentSearchSuggestions.get(highlightedSuggestionIndex));
                    e.consume();
                }
            } else if (e.getCode() == KeyCode.ESCAPE) {
                searchSuggestionsPopup.hide();
                e.consume();
            }
        });
    }

    private void initStatusFilter() {
        statusFilter = new ComboBox<>(FXCollections.observableArrayList(
                "All statuses", "Healthy", "Monitoring", "At Risk", "Destroyed"));
        statusFilter.setValue("All statuses");
        statusFilter.setPrefWidth(150);
        statusFilter.setStyle("-fx-background-radius: 10; -fx-background-color: white;");
        statusFilter.valueProperty().addListener((obs, oldStatus, newStatus) -> {
            performSearch();
            refreshSearchSuggestions();
        });
    }

    private void performSearch() {
        String query = txtSearch.getText() == null ? "" : txtSearch.getText().trim();
        String queryLower = query.toLowerCase(Locale.ROOT);
        String numericQuery = normalizeNumericQuery(query);
        String statusChoice = statusFilter != null ? statusFilter.getValue() : "All statuses";
        List<Tree> filtered = cachedTrees.stream()
                .filter(t -> "All statuses".equals(statusChoice)
                        || (t.status != null && t.status.equalsIgnoreCase(statusChoice)))
                .filter(t -> {
                    if (query.isEmpty()) {
                        return true;
                    }
                    String species = t.species == null ? "" : t.species.toLowerCase(Locale.ROOT);
                    String idText = String.valueOf(t.id);
                    boolean speciesMatch = species.contains(queryLower);
                    boolean idMatch = !numericQuery.isEmpty() && idText.contains(numericQuery);
                    return speciesMatch || idMatch;
                })
                .collect(Collectors.toList());
        renderTreeList(filtered);
    }

    private String normalizeNumericQuery(String rawQuery) {
        if (rawQuery == null) {
            return "";
        }
        String trimmed = rawQuery.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1).trim();
        }
        if (trimmed.isEmpty() || !trimmed.matches("\\d+")) {
            return "";
        }
        return trimmed;
    }

    private void refreshSearchSuggestions() {
        if (txtSearch == null) {
            return;
        }
        String query = txtSearch.getText();
        if (query == null) {
            query = "";
        }
        String queryLower = query.toLowerCase(Locale.ROOT).trim();
        String numericQuery = normalizeNumericQuery(query);
        String statusChoice = statusFilter != null ? statusFilter.getValue() : "All statuses";

        currentSearchSuggestions.clear();
        cachedTrees.stream()
                .filter(t -> "All statuses".equals(statusChoice)
                        || (t.status != null && t.status.equalsIgnoreCase(statusChoice)))
                .filter(t -> {
                    if (queryLower.isEmpty()) {
                        return true;
                    }
                    String species = t.species == null ? "" : t.species.toLowerCase(Locale.ROOT);
                    String idText = String.valueOf(t.id);
                    boolean speciesMatch = species.contains(queryLower);
                    boolean idMatch = !numericQuery.isEmpty() && idText.contains(numericQuery);
                    return speciesMatch || idMatch;
                })
                .sorted(Comparator.comparingInt((Tree t) -> t.id).reversed())
                .limit(5)
                .forEach(currentSearchSuggestions::add);

        if (!txtSearch.isFocused() || currentSearchSuggestions.isEmpty()) {
            highlightedSuggestionIndex = -1;
            searchSuggestionsPopup.hide();
            return;
        }

        if (highlightedSuggestionIndex < 0 || highlightedSuggestionIndex >= currentSearchSuggestions.size()) {
            highlightedSuggestionIndex = 0;
        }
        rebuildSuggestionPopupItems();
        if (!searchSuggestionsPopup.isShowing()) {
            searchSuggestionsPopup.show(txtSearch, Side.BOTTOM, 0, 2);
        }
    }

    private void moveSuggestionHighlight(int delta) {
        if (currentSearchSuggestions.isEmpty()) {
            return;
        }
        int size = currentSearchSuggestions.size();
        highlightedSuggestionIndex = (highlightedSuggestionIndex + delta + size) % size;
        rebuildSuggestionPopupItems();
    }

    private void rebuildSuggestionPopupItems() {
        searchSuggestionsPopup.getItems().clear();
        for (int i = 0; i < currentSearchSuggestions.size(); i++) {
            final int idx = i;
            Tree t = currentSearchSuggestions.get(i);
            String species = (t.species == null || t.species.trim().isEmpty()) ? "(unnamed tree)" : t.species.trim();
            Label row = new Label(species + "  #" + t.id);
            row.setMinWidth(300);
            row.setMaxWidth(Double.MAX_VALUE);
            row.setStyle(i == highlightedSuggestionIndex
                    ? "-fx-padding: 8 10; -fx-background-color: #E8F5E9; -fx-text-fill: #20865B; -fx-font-weight: bold;"
                    : "-fx-padding: 8 10; -fx-background-color: white; -fx-text-fill: #20865B;");
            CustomMenuItem item = new CustomMenuItem(row, true);
            item.setOnAction(evt -> applySuggestion(currentSearchSuggestions.get(idx)));
            searchSuggestionsPopup.getItems().add(item);
        }
    }

    private void applySuggestion(Tree t) {
        if (t == null) {
            return;
        }
        txtSearch.setText(String.valueOf(t.id));
        txtSearch.positionCaret(txtSearch.getText().length());
        highlightedSuggestionIndex = -1;
        searchSuggestionsPopup.hide();
        performSearch();
    }

    private VBox sidebar(BorderPane root) {
        VBox side = new VBox(5);
        side.setPrefWidth(192);
        side.getStyleClass().add("sidebar");
        side.getChildren().addAll(RegistryStyle.brand(true), new Separator(), RegistryStyle.caption("WORKSPACE"));
        registryButton = nav("Registry", this::showRegistry);
        Button messages = nav("Messages", this::openMembersDialog);
        side.getChildren().addAll(registryButton,
                nav("Field Map", () -> root.setCenter(FieldMapStage.view(new ArrayList<>(cachedTrees)))),
                messages, nav("Profile", this::openProfileDialog));
        chatBadgeLabel = new Label();
        configureBadgeLabel(chatBadgeLabel);
        messages.setGraphic(new HBox(4, RegistryStyle.navIcon("Messages"), chatBadgeLabel));
        if (isAdmin) {
            side.getChildren().add(RegistryStyle.caption("ADMINISTRATION"));
            Button pending = nav("Pending Review", this::fetchAndShowPending);
            pendingBadgeLabel = new Label(); configureBadgeLabel(pendingBadgeLabel); pending.setGraphic(new HBox(4, RegistryStyle.navIcon("Pending Review"), pendingBadgeLabel));
            side.getChildren().addAll(pending, nav("Manage Users", this::openAccountManager),
                    nav("Archive", this::fetchAndShowDeleted), nav("Ranger Access", () -> {
                        root.setCenter(new VBox(18, RegistryStyle.heading("Ranger Access"), createServerControlPanel()));
                        fetchServerStatusOnBackground();
                    }), nav("System States", () -> {
                        BorderPane panel = createActivityLogPanel();
                        panel.setTop(RegistryStyle.heading("System States"));
                        root.setCenter(panel);
                        dataExecutor.execute(() -> {
                            try {
                                String logs = String.join("\n", adminService.getActivityLog(sessionToken));
                                Platform.runLater(() -> logArea.setText(logs));
                            } catch (Exception ex) { handleRemoteFailure(ex, "Activity log"); }
                        });
                    }));
        }
        if (!isAdmin) side.getChildren().add(nav("My submissions", () -> {
            ListView<String> submissions = new ListView<>();
            submissions.setPlaceholder(new Label("No submissions yet."));
            BorderPane panel = new BorderPane(submissions);
            panel.setPadding(new Insets(24)); panel.setTop(RegistryStyle.heading("My submissions"));
            root.setCenter(panel);
            dataExecutor.execute(() -> {
                try {
                    List<String> items = treeService.getMySubmissions(sessionToken);
                    Platform.runLater(() -> submissions.getItems().setAll(items));
                } catch (Exception ex) { handleRemoteFailure(ex, "My submissions"); }
            });
        }));
        Region space = new Region(); VBox.setVgrow(space, Priority.ALWAYS);
        reconnect.setManaged(false); reconnect.setVisible(false);
        reconnect.setOnAction(e -> { close(); TreeClient.launchConnection(); });
        side.getChildren().add(space);
        if (isAdmin) side.getChildren().add(onlineUsers);
        side.getChildren().addAll(connection, reconnect, new Separator(), new Label(username), nav("Sign Out", this::handleSignOut));
        return side;
    }
    private Button nav(String text, Runnable action) {
        Button b = new Button(text); b.setGraphic(RegistryStyle.navIcon(text)); b.setGraphicTextGap(10);
        b.getStyleClass().add("nav"); b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> {
            if (!text.equals("Sign Out")) selectNav(b);
            action.run();
        });
        return b;
    }
    private void selectNav(Button button) {
        if (selectedNav != null) selectedNav.getStyleClass().remove("nav-active");
        selectedNav = button;
        button.getStyleClass().add("nav-active");
    }
    void showRegistry() { selectNav(registryButton); workspace.setCenter(inventory()); }

    private BorderPane inventory() {
        VBox heading = new VBox(4, RegistryStyle.heading("Forest Registry"), new Label("Welcome back, " + username + "."));
        heading.getStyleClass().add("topbar");
        statsWrapper = new BorderPane(); statsWrapper.setTop(createStatsPanel());
        Label title = new Label("Tree inventory"); title.setStyle("-fx-font-size: 18px;");
        HBox toolbar = new HBox(10, new VBox(4, title, inventoryCount), RegistryStyle.spacer(),
                RegistryStyle.primary("+  Register Sapling", this::addNewTree));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        HBox filters = new HBox(6,txtSearch, RegistryStyle.spacer());
        ToggleGroup group = new ToggleGroup();
        for (String choice : statusFilter.getItems()) {
            ToggleButton b = new ToggleButton(choice.equals("All statuses") ? "All" : choice);
            b.setToggleGroup(group); b.getStyleClass().add("nav");
            b.setSelected(choice.equals(statusFilter.getValue()));
            b.setOnAction(e -> { b.setSelected(true); statusFilter.setValue(choice); });
            filters.getChildren().add(b);
        }
        cardContainer = new GridPane(); cardContainer.setHgap(12); cardContainer.setVgap(12);
        for (int i=0; i<2; i++) {
            ColumnConstraints col = new ColumnConstraints(); col.setPercentWidth(50); cardContainer.getColumnConstraints().add(col);
        }
        VBox body = new VBox(16, statsWrapper, toolbar, filters, cardContainer);
        body.setPadding(new Insets(22,28,22,28));
        ScrollPane scroll = new ScrollPane(body); scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #f5f7f5; -fx-background-color: transparent;");
        BorderPane panel = new BorderPane(scroll); panel.setTop(heading);
        performSearch(); return panel;
    }

    private static String statusAccent(String status) {
        if (status == null) {
            return "#6B8E23";
        }
        String s = status.toLowerCase(Locale.ROOT);
        if (s.contains("healthy")) {
            return "#4F772D";
        }
        if (s.contains("monitor")) {
            return "#457B9D";
        }
        if (s.contains("risk")) {
            return "#BC6C25";
        }
        if (s.contains("destroy")) {
            return "#9D2235";
        }
        return "#6B8E23";
    }

    /** If RMI left bytes empty, try local {@code data/images} (same folder layout as server). */
    private static void hydrateTreeImageLocal(Tree t) {
        if (t == null) {
            return;
        }
        if (t.imageBytes != null && t.imageBytes.length > 0) {
            return;
        }
        if (t.imagePath != null && !t.imagePath.trim().isEmpty()) {
            t.imageBytes = ImageStorage.loadImage(t.imagePath);
        }
        if (t.imageBytes == null || t.imageBytes.length == 0) {
            t.imageBytes = ImageStorage.loadTreeImageById(t.id);
        }
    }

    private void addTreeCard(Tree t) {
        hydrateTreeImageLocal(t);
        BorderPane card = new BorderPane();
        card.getStyleClass().add("card");
        card.setPadding(new Insets(10));
        ImageView photo = new ImageView();
        StackPane picture = new StackPane();
        picture.setMinSize(92,94); picture.setPrefSize(104,104);
        picture.setStyle("-fx-background-color: #e4ede5; -fx-background-radius: 8;");
        if (t.imageBytes != null && t.imageBytes.length > 0) {
            Image image = bytesToImage(t.imageBytes);
            if (image != null && !image.isError()) {
                double size = Math.min(image.getWidth(), image.getHeight());
                photo.setViewport(new javafx.geometry.Rectangle2D((image.getWidth()-size)/2, (image.getHeight()-size)/2, size, size));
                photo.setImage(image); photo.setFitWidth(94); photo.setFitHeight(94);
                picture.getChildren().add(photo);
            }
        }
        if (picture.getChildren().isEmpty()) picture.getChildren().add(RegistryStyle.leaf());
        Label species = new Label(t.species); species.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        Label condition = new Label(t.status, new Circle(4,Color.web(statusAccent(t.status))));
        condition.setStyle("-fx-font-size: 11px; -fx-background-color: #eff4f0; -fx-padding: 3 7; -fx-background-radius: 12;");
        Label measurements = new Label(t.height + " m");
        Label location = new Label("#" + t.id + "  ·  " + t.location); location.setWrapText(true);
        location.getStyleClass().add("muted");
        VBox info = new VBox(5, species, condition, measurements, location);
        info.setPadding(new Insets(3,0,3,12));
        card.setLeft(picture); card.setCenter(info);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (canModifyTree(t)) {
            Button btnEdit = createCardIconButton(editActionIcon, "E", RIVER_BLUE, "Edit");
            btnEdit.setOnAction(e -> { e.consume(); editTree(t); });
            Button btnDel = createCardIconButton(deleteActionIcon, "D", DELETE_RED, "Delete");
            btnDel.setOnAction(e -> { e.consume(); deleteTree(t); });
            actions.getChildren().addAll(btnEdit, btnDel);
        }

        card.setBottom(actions);
        card.setOnMouseClicked(_ -> new TreeDialogs.TreeDetailDialog(this, t, this::canModifyTree, this::editTree, SDG_GREEN, RIVER_BLUE, PANEL_DARK).showAndWait());
        int index = cardContainer.getChildren().size();
        cardContainer.add(card, index % 2, index / 2);
    }

    private HBox createStatsPanel() {
        HBox panel = new HBox(12);
        panel.setAlignment(Pos.CENTER_LEFT);
        panel.setPadding(new Insets(0, 0, 5, 0));
        long total = cachedTrees.size();
        long healthy = countByStatus("Healthy");
        long monitoring = countByStatus("Monitoring");
        long atRisk = countByStatus("At Risk");
        panel.getChildren().addAll(
                createStatBox("Total Trees", total, PANEL_DARK, "In the registry"),
                createStatBox("Healthy", healthy, SDG_GREEN, percentage(healthy, total)),
                createStatBox("Monitoring", monitoring, RIVER_BLUE, percentage(monitoring, total)),
                createStatBox("At Risk", atRisk, "#BC6C25", percentage(atRisk, total))
        );
        if (isAdmin) onlineUsers.setText("Active users · " + activeUsersCount);
        return panel;
    }

    private static String percentage(long count, long total) {
        return (total == 0 ? 0 : Math.round(100.0 * count / total)) + "% of inventory";
    }

    private VBox createStatBox(String title, long value, String accent, String summary) {
        VBox box = new VBox(4);
        box.setPrefWidth(180);
        HBox.setHgrow(box, Priority.ALWAYS);
        box.getStyleClass().add("stat");
        Label heading = new Label(title);
        Label count = new Label(String.valueOf(value));
        count.setStyle("-fx-font-size: 27px; -fx-text-fill: " + accent + ";");
        Label detail = new Label(summary);
        detail.getStyleClass().add("muted");
        box.getChildren().addAll(heading, count, detail);
        return box;
    }

    private long countByStatus(String expected) {
        return cachedTrees.stream()
                .filter(t -> normalizeStatus(t.status).equalsIgnoreCase(expected))
                .count();
    }

    private static String normalizeStatus(String status) {
        if (status == null) {
            return "";
        }
        String s = status.trim().toLowerCase(Locale.ROOT);
        if (s.contains("healthy")) {
            return "Healthy";
        }
        if (s.contains("monitor")) {
            return "Monitoring";
        }
        if (s.contains("risk")) {
            return "At Risk";
        }
        if (s.contains("destroy")) {
            return "Destroyed";
        }
        return status.trim();
    }

    private void styleButton(Button btn, String bg) {
        btn.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-background-radius: 8; -fx-padding: 8 15; -fx-cursor: hand;");
    }

    private Button createCardIconButton(Image iconImage, String fallbackIconText, String color, String tooltipText) {
        Button btn = new Button();
        btn.setTooltip(new Tooltip(tooltipText));
        btn.setMinSize(30, 30);
        btn.setPrefSize(30, 30);
        btn.setMaxSize(30, 30);
        btn.setStyle("-fx-background-color: white; -fx-border-color: " + color + "; -fx-border-radius: 15; -fx-background-radius: 15; -fx-text-fill: " + color + "; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand;");
        if (iconImage != null) {
            ImageView iconView = new ImageView(iconImage);
            iconView.setFitWidth(16);
            iconView.setFitHeight(16);
            iconView.setPreserveRatio(true);
            btn.setGraphic(iconView);
        } else {
            btn.setText(fallbackIconText);
        }
        return btn;
    }

    private Image loadUiIcon(String classpathPath, String fallbackFilePath) {
        try (InputStream in = TreeDashboard.class.getResourceAsStream(classpathPath)) {
            if (in != null) {
                return new Image(in);
            }
        } catch (IOException ignored) {
        }
        try (InputStream in = new FileInputStream(fallbackFilePath)) {
            return new Image(in);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void configureBadgeLabel(Label badge) {
        badge.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px; -fx-background-color: " + DELETE_RED + "; -fx-background-radius: 999; -fx-alignment: center; -fx-min-width: 18; -fx-min-height: 18; -fx-padding: 0 5 0 5;");
        badge.setVisible(false);
        badge.setManaged(false);
    }

    private void updateBadgeCount(Label badge, int count) {
        if (badge == null) {
            return;
        }
        if (count > 0) {
            badge.setText(count > 99 ? "99+" : String.valueOf(count));
            badge.setVisible(true);
            badge.setManaged(true);
        } else {
            badge.setText("");
            badge.setVisible(false);
            badge.setManaged(false);
        }
    }

    private Image bytesToImage(byte[] b) {
        return (b == null || b.length == 0) ? null : new Image(new ByteArrayInputStream(b));
    }

    // --- Original Logic Wrappers (Required for RMI functionality) ---
    private void startNotificationTimers() {
        if (isAdmin) {
            Timeline pendingBadgeTimer = new Timeline(new KeyFrame(Duration.seconds(3), _ -> refreshPendingBadge()));
            pendingBadgeTimer.setCycleCount(Timeline.INDEFINITE); pendingBadgeTimer.play(); timers.add(pendingBadgeTimer);
            refreshPendingBadge();
            activeUsersTimer = new Timeline(new KeyFrame(Duration.seconds(3), _ -> refreshActiveUsersCount()));
            activeUsersTimer.setCycleCount(Timeline.INDEFINITE);
            activeUsersTimer.play();
            refreshActiveUsersCount();
        }
        Timeline chatBadgeTimer = new Timeline(new KeyFrame(Duration.seconds(2), _ -> refreshChatBadge()));
        chatBadgeTimer.setCycleCount(Timeline.INDEFINITE); chatBadgeTimer.play(); timers.add(chatBadgeTimer);
        refreshChatBadge();
    }

    private void refreshPendingBadge() {
        if (!isAdmin || pendingBadgeLabel == null) return;
        dataExecutor.execute(() -> {
            try {
                ArrayList<Tree> pending = treeService.getPendingTrees(sessionToken);
                int count = pending == null ? 0 : pending.size();
                Platform.runLater(() -> updateBadgeCount(pendingBadgeLabel, count));
            } catch (Exception ignored) {}
        });
    }

    private void refreshChatBadge() {
        if (chatService == null || chatBadgeLabel == null) return;
        dataExecutor.execute(() -> {
            try {
                int unread = chatService.getUnreadCount(sessionToken);
                Platform.runLater(() -> updateBadgeCount(chatBadgeLabel, unread));
            } catch (Exception ignored) {}
        });
    }

    private void refreshActiveUsersCount() {
        if (!isAdmin || chatService == null) {
            return;
        }
        dataExecutor.execute(() -> {
            try {
                int count = chatService.getOnlineUsers(sessionToken).size();
                Platform.runLater(() -> {
                    if (activeUsersCount != count) {
                        activeUsersCount = count;
                        if (statsWrapper != null) {
                            statsWrapper.setTop(createStatsPanel());
                        }
                    }
                });
            } catch (Exception ignored) {
            }
        });
    }

    private volatile int consecutiveRefreshFailures = 0;

    private void loadInitialData() {
        if (!fetchInProgress.compareAndSet(false, true)) return;
        dataExecutor.execute(() -> {
            try {
                ArrayList<Tree> newTrees = treeService.getAllTrees(sessionToken);
                consecutiveRefreshFailures = 0;
                Platform.runLater(() -> {
                    connection.setText("Server online");
                    reconnect.setVisible(false);
                    reconnect.setManaged(false);
                    cachedTrees.clear(); cachedTrees.addAll(newTrees);
                    performSearch();
                    refreshSearchSuggestions();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> { connection.setText("Server unavailable"); reconnect.setManaged(true); reconnect.setVisible(true); });
                consecutiveRefreshFailures++;
                if (consecutiveRefreshFailures <= 1) {
                    System.err.println("[Dashboard] Auto-refresh failed: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
                }
            } finally { fetchInProgress.set(false); }
        });
    }

    private void startInventoryAutoRefresh() {
        inventoryRefreshTimer = new Timeline(new KeyFrame(Duration.seconds(5), _ -> loadInitialData()));
        inventoryRefreshTimer.setCycleCount(Timeline.INDEFINITE); inventoryRefreshTimer.play();
    }

    private void renderTreeList(List<Tree> list) {
        cardContainer.getChildren().clear();
        inventoryCount.setText(list.size() + (list.size() == 1 ? " record" : " records"));
        for (Tree t : list) addTreeCard(t);
        if (list.isEmpty()) cardContainer.add(new Label("No matching tree records."), 0, 0);
        if (statsWrapper != null) statsWrapper.setTop(createStatsPanel());
    }

    private void addNewTree() {
        AddTreeDialog d = new AddTreeDialog(this, null, username); d.showAndWait();
        Tree t = d.getTree();
        if (t != null) {
            new Thread(() -> {
                try {
                    String resp = treeService.addTree(t, sessionToken);
                    Platform.runLater(() -> {
                        if (resp != null && resp.startsWith("SUCCESS")) {
                            Alert ok = new Alert(Alert.AlertType.INFORMATION);
                            ok.setHeaderText(null);
                            if ("admin".equals(username)) {
                                ok.setTitle("Tree Added");
                                ok.setContentText("Tree has been added to the active inventory.\n" + resp);
                            } else {
                                ok.setTitle("Submission Received");
                                ok.setContentText("Your tree entry is now pending admin review. It will appear in the grove once approved.\n" + resp);
                            }
                            ok.showAndWait();
                            loadInitialData();
                        } else {
                            Alert err = new Alert(Alert.AlertType.ERROR,
                                    resp != null ? resp : "Add tree failed.");
                            err.setHeaderText(null);
                            err.showAndWait();
                        }
                    });
                } catch (Exception ex) { handleRemoteFailure(ex, "Registry Error"); }
            }).start();
        }
    }

    private void editTree(Tree t) {
        AddTreeDialog d = new AddTreeDialog(this, t, username); d.showAndWait();
        Tree updated = d.getTree();
        if (updated != null) {
            new Thread(() -> {
                try {
                    String result = treeService.updateTree(updated, sessionToken);
                    if (!result.startsWith("SUCCESS")) throw new IllegalStateException(result);
                    loadInitialData();
                } catch (Exception ex) { handleRemoteFailure(ex, "Update Error"); }
            }).start();
        }
    }

    private void deleteTree(Tree t) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Permanently remove \"" + t.species + "\" from the grove?\n"
                        + "The record will be archived in deleted tree data (admins can review it).",
                ButtonType.YES, ButtonType.NO);
        a.setHeaderText(null);
        a.showAndWait().ifPresent(r -> {
            if (r != ButtonType.YES) {
                return;
            }
            new Thread(() -> {
                try {
                    String resp = treeService.deleteTree(t.id, sessionToken);
                    Platform.runLater(() -> {
                        if (resp != null && resp.startsWith("SUCCESS")) {
                            Alert ok = new Alert(Alert.AlertType.INFORMATION,
                                    "Tree removed from inventory and added to the deleted tree archive.");
                            ok.setHeaderText(null);
                            ok.showAndWait();
                            loadInitialData();
                        } else {
                            Alert err = new Alert(Alert.AlertType.ERROR,
                                    resp != null ? resp : "Delete failed.");
                            err.setHeaderText(null);
                            err.showAndWait();
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> handleRemoteFailure(ex, "Delete Error"));
                }
            }, "TreeDelete").start();
        });
    }

    private void performLogout() {
        try { userService.signOut(sessionToken); } catch (Exception ignored) {}
        if (inventoryRefreshTimer != null) inventoryRefreshTimer.stop();
        if (activeUsersTimer != null) activeUsersTimer.stop();
        close();
        if (isAdmin) {
            try {
                Stage launcher = (Stage) Class.forName("client_GUI.ServerLauncherGUI")
                        .getConstructor().newInstance();
                launcher.show();
            } catch (Exception e) {
                TreeClient.launchConnection();
            }
        } else {
            TreeClient.launchConnection();
        }
    }

    private void handleRemoteFailure(Throwable ex, String msg) {
        ex.printStackTrace();
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR, msg + ": " + ex.getMessage());
            a.showAndWait();
        });
    }

    // --- Dialog/Service Helpers ---
    private void openAccountManager() {
        workspace.setCenter(new TreeDialogs.AccountManagerDialog(this, userService, username, sessionToken, this::handleRemoteFailure, RIVER_BLUE, DELETE_RED, PANEL_DARK));
    }
    private void openProfileDialog() {
        workspace.setCenter(new TreeDialogs.ProfileDialog(this, userService, username, sessionToken, SDG_GREEN, PANEL_DARK, RIVER_BLUE));
    }
    void openMembersDialog() {
        MembersDialog members = new MembersDialog(this, chatService, username, sessionToken, dataExecutor,
                this::resolveDisplayName, this::openChat, PANEL_DARK, RIVER_BLUE);
        messagesPane = new SplitPane(members, new StackPane(new Label("Select a member to open a conversation.")));
        messagesPane.setDividerPositions(0.36);
        workspace.setCenter(messagesPane);
    }

    private void openChat(String peer) {
        ChatDialog chat = new ChatDialog(this, peer, username, sessionToken, isAdmin, chatService,
                userService, dataExecutor, PANEL_DARK, RIVER_BLUE, SDG_GREEN);
        messagesPane.getItems().set(1, chat);
    }
    private String resolveDisplayName(String u) {
        if (displayNameCache.containsKey(u)) return displayNameCache.get(u);
        try {
            UserProfile p = userService.getProfile(sessionToken, u);
            String n = (p != null && p.displayName != null) ? p.displayName : u;
            displayNameCache.put(u, n); return n;
        } catch (Exception e) { return u; }
    }
    private boolean canModifyTree(Tree t) { return isAdmin || (t.ownerUsername != null && t.ownerUsername.equals(username)); }

    private BorderPane createServerControlPanel() {
        BorderPane p = new BorderPane(); p.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-padding: 15;");
        lblServerStatus = new Label("Checking link...");
        Button btnOn = new Button("Enable sign-in"); styleButton(btnOn, SDG_GREEN);
        btnOn.setOnAction(_ -> runServerCommand("SERVER_START"));
        Button btnOff = new Button("Pause sign-in"); styleButton(btnOff, DELETE_RED);
        btnOff.setOnAction(_ -> runServerCommand("SERVER_STOP"));
        HBox c = new HBox(10, lblServerStatus, btnOn, btnOff); c.setAlignment(Pos.CENTER_LEFT);
        p.setCenter(c); return p;
    }

    private void fetchServerStatusOnBackground() {
        serverControlExecutor.execute(() -> {
            try {
                String s = adminService.getServerStatus();
                Platform.runLater(() -> lblServerStatus.setText("STATUS_ON".equals(s) ? "Ranger sign-in enabled" : "Ranger sign-in paused"));
            } catch (Exception e) {
                Platform.runLater(() -> lblServerStatus.setText("Status: Disconnected"));
            }
        });
    }

    private void runServerCommand(String cmd) {
        serverControlExecutor.execute(() -> {
            try {
                if ("SERVER_START".equals(cmd)) adminService.enableServerLogin(sessionToken);
                else adminService.disableServerLogin(sessionToken);
                fetchServerStatusOnBackground();
            } catch (Exception e) { handleRemoteFailure(e, "Command Failed"); }
        });
    }

    private BorderPane createActivityLogPanel() {
        BorderPane p = new BorderPane(); p.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-padding: 10;");
        logArea = new TextArea(); logArea.setEditable(false); logArea.setStyle("-fx-font-family: 'Monospace'; -fx-font-size: 11px;");
        p.setCenter(logArea); return p;
    }

    private void fetchAndShowDeleted() {
        try {
            ArrayList<Tree> d = treeService.getDeletedTrees(sessionToken);
            workspace.setCenter(new TreeDialogs.DeletedTreesDialog(this, d, PANEL_DARK));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void fetchAndShowPending() {
        try {
            ArrayList<Tree> p = treeService.getPendingTrees(sessionToken);
            workspace.setCenter(new TreeDialogs.PendingTreesDialog(this, p, treeService, username, sessionToken, this::resolveDisplayName, this::handleRemoteFailure, this::loadInitialData, this::fetchAndShowPending, () -> {}, RIVER_BLUE, PANEL_DARK, SDG_GREEN, DELETE_RED));
        } catch (Exception e) { handleRemoteFailure(e, "Pending Load Failed"); }
    }

    private void handleSignOut() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Sign out of The Lorax?", ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(this);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) performLogout();
    }

}
