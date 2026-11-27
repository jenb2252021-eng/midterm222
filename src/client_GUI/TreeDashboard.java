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
import javafx.scene.shape.SVGPath;
import javafx.scene.chart.*;
import javafx.scene.Node;
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
import java.util.Map;
import java.util.LinkedHashMap;
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
    private Button map3dButton;
    /** 3D Forest Map; created on first use and kept so camera and selection survive navigation. */
    private TreeMap3D map3d;
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
        setMinWidth(760);
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
            if (map3d != null) map3d.dispose();
        });
        setScene(scene);
        widthProperty().addListener((o, a, w) -> { if (!compactManual) setCompactSidebar(w.doubleValue() < 1150); });

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
        txtSearch.setPrefWidth(320);
        txtSearch.setPrefHeight(38);
        txtSearch.setPromptText("Search trees, species, or ID");
        txtSearch.getStyleClass().add("search-field");
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
        String sort = sortChoice == null ? SORTS[0] : sortChoice.getValue();
        String key = query + "|" + statusChoice + "|" + yearFilter + "|" + sort;
        if (!key.equals(lastFilterKey)) { page = 0; lastFilterKey = key; }
        List<Tree> filtered = cachedTrees.stream()
                .filter(t -> "All statuses".equals(statusChoice)
                        || (t.status != null && normalizeStatus(t.status).equalsIgnoreCase(statusChoice)))
                .filter(t -> yearFilter == null || yearFilter.equals(plantedYear(t)))
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
                .sorted(comparatorFor(sort))
                .collect(Collectors.toList());
        renderTreeList(filtered);
    }

    private static final String[] SORTS = {"Newest first", "Species A–Z", "Tallest first",
            "Needs attention first", "Recently planted", "Oldest planted"};

    private static Comparator<Tree> comparatorFor(String sort) {
        Comparator<Tree> byId = Comparator.comparingInt((Tree t) -> t.id).reversed();
        if (sort == null) return byId;
        return switch (sort) {
            case "Species A–Z" -> Comparator.comparing((Tree t) -> t.species == null ? "" : t.species.toLowerCase(Locale.ROOT)).thenComparing(byId);
            case "Tallest first" -> Comparator.comparingDouble((Tree t) -> t.height).reversed().thenComparing(byId);
            case "Needs attention first" -> Comparator.comparingInt((Tree t) -> attentionRank(t.status)).thenComparing(byId);
            case "Recently planted" -> Comparator.comparing((Tree t) -> t.plantedDate == null ? "" : t.plantedDate).reversed().thenComparing(byId);
            case "Oldest planted" -> Comparator.comparing((Tree t) -> t.plantedDate == null || t.plantedDate.isBlank() ? "9999" : t.plantedDate).thenComparing(byId);
            default -> byId;
        };
    }

    /** Destroyed and at-risk trees first, healthy last. */
    private static int attentionRank(String status) {
        return switch (normalizeStatus(status)) {
            case "At Risk" -> 0;
            case "Destroyed" -> 1;
            case "Monitoring" -> 2;
            case "Healthy" -> 4;
            default -> 3;
        };
    }

    private static Integer plantedYear(Tree t) {
        if (t.plantedDate == null || t.plantedDate.length() < 4) return null;
        try { return Integer.parseInt(t.plantedDate.substring(0, 4)); } catch (NumberFormatException e) { return null; }
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
        side.setPrefWidth(200);
        side.setMinWidth(Region.USE_PREF_SIZE);
        side.getStyleClass().add("sidebar");
        sidebarBox = side;
        HBox brand = RegistryStyle.brand(true);
        brandText = brand.getChildren().get(1);
        Button collapse = new Button();
        SVGPath burger = new SVGPath();
        burger.setContent("M2 4h14 M2 9h14 M2 14h14");
        burger.setStyle("-fx-stroke: #42534a; -fx-stroke-width: 1.6; -fx-stroke-line-cap: round;");
        collapse.setGraphic(burger);
        collapse.getStyleClass().add("icon-button");
        collapse.setTooltip(new Tooltip("Collapse or expand the sidebar"));
        collapse.setOnAction(e -> { compactManual = true; setCompactSidebar(!compactSidebar); });
        HBox top = new HBox(4, brand, RegistryStyle.spacer(), collapse);
        top.setAlignment(Pos.CENTER_LEFT);
        Label workspaceCaption = RegistryStyle.caption("WORKSPACE");
        sidebarCaptions.add(workspaceCaption);
        side.getChildren().addAll(top, new Separator(), workspaceCaption);
        registryButton = nav("Registry", this::showRegistry);
        Button messages = nav("Messages", this::openMembersDialog);
        map3dButton = nav("3D Map", this::show3DMap);
        side.getChildren().addAll(registryButton, map3dButton, messages, nav("Profile", this::openProfileDialog));
        chatBadgeLabel = new Label();
        configureBadgeLabel(chatBadgeLabel);
        messages.setGraphic(new HBox(4, RegistryStyle.navIcon("Messages"), chatBadgeLabel));
        if (isAdmin) {
            // Expandable section: click the heading to show or hide the administration pages.
            VBox adminBox = new VBox(5);
            Button adminHeader = new Button("ADMINISTRATION  ▾");
            adminHeader.getStyleClass().add("section-toggle");
            adminHeader.setMaxWidth(Double.MAX_VALUE);
            adminHeader.setOnAction(e -> {
                boolean show = !adminBox.isVisible();
                adminBox.setVisible(show); adminBox.setManaged(show);
                adminHeader.setText("ADMINISTRATION  " + (show ? "▾" : "▸"));
            });
            sidebarCaptions.add(adminHeader);
            side.getChildren().addAll(adminHeader, adminBox);
            Button pending = nav("Pending Review", this::fetchAndShowPending);
            pendingBadgeLabel = new Label(); configureBadgeLabel(pendingBadgeLabel); pending.setGraphic(new HBox(4, RegistryStyle.navIcon("Pending Review"), pendingBadgeLabel));
            adminBox.getChildren().addAll(pending, nav("Manage Users", this::openAccountManager),
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
        // Connection status with a live coloured dot.
        Circle dot = new Circle(4, Color.web("#9aa6a0"));
        connection.setGraphic(dot);
        connection.setGraphicTextGap(8);
        connection.getStyleClass().add("connection-status");
        connection.textProperty().addListener((o, a, text) -> dot.setFill(Color.web(
                text != null && text.toLowerCase(Locale.ROOT).contains("online") ? "#2fa36f"
                        : text != null && text.toLowerCase(Locale.ROOT).contains("unavailable") ? "#c0392b" : "#9aa6a0")));
        onlineUsers.getStyleClass().add("muted");
        if (isAdmin) side.getChildren().add(onlineUsers);
        side.getChildren().addAll(connection, reconnect, new Separator(), userChip(), nav("Sign Out", this::handleSignOut));
        return side;
    }

    /** Avatar initial, name and role at the foot of the sidebar. */
    private HBox userChip() {
        String initial = username == null || username.isBlank() ? "?" : username.substring(0, 1).toUpperCase(Locale.ROOT);
        Label avatar = new Label(initial);
        avatar.getStyleClass().add("avatar");
        Label name = new Label(username);
        name.getStyleClass().add("user-name");
        Label role = new Label(isAdmin ? "Administrator" : "Ranger");
        role.getStyleClass().add("muted");
        HBox chip = new HBox(10, avatar, new VBox(1, name, role));
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("user-chip");
        userChipBox = chip;
        return chip;
    }
    private VBox sidebarBox;
    private Node brandText;
    private final List<Node> sidebarCaptions = new ArrayList<>();
    private final List<Button> navButtons = new ArrayList<>();
    private HBox userChipBox;
    private boolean compactSidebar = false;
    private boolean compactManual = false;

    /** Icon-only sidebar for narrow windows (or on demand); every item keeps a tooltip with its name. */
    private void setCompactSidebar(boolean compact) {
        if (sidebarBox == null) return;
        compactSidebar = compact;
        sidebarBox.setPrefWidth(compact ? 72 : 200);
        if (brandText != null) { brandText.setVisible(!compact); brandText.setManaged(!compact); }
        for (Node c : sidebarCaptions) { c.setVisible(!compact); c.setManaged(!compact); }
        for (Button b : navButtons) b.setContentDisplay(compact ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
        connection.setContentDisplay(compact ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
        onlineUsers.setVisible(!compact); onlineUsers.setManaged(!compact && isAdmin);
        if (userChipBox != null && userChipBox.getChildren().size() > 1) {
            Node text = userChipBox.getChildren().get(1);
            text.setVisible(!compact); text.setManaged(!compact);
        }
    }

    private Button nav(String text, Runnable action) {
        Button b = new Button(text); b.setGraphic(RegistryStyle.navIcon(text)); b.setGraphicTextGap(10);
        b.setTooltip(new Tooltip(text));
        navButtons.add(b);
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

    // ---------------------------------------------------------------- 3D Forest Map integration

    private void show3DMap() {
        selectNav(map3dButton);
        if (map3d == null) {
            map3d = new TreeMap3D(new ArrayList<>(cachedTrees), new TreeMap3D.Host() {
                @Override public boolean canEdit(Tree t) { return canModifyTree(t); }
                @Override public void openRecord(Tree t) {
                    hydrateTreeImageLocal(t);
                    new TreeDialogs.TreeDetailDialog(TreeDashboard.this, t, TreeDashboard.this::canModifyTree,
                            TreeDashboard.this::editTree, SDG_GREEN, RIVER_BLUE, PANEL_DARK).showAndWait();
                }
                @Override public void editRecord(Tree t) { editTree(t); }
                @Override public void showInRegistry(Tree t) { showTreeInRegistry(t); }
                @Override public List<String> loadHistory(int treeId) throws Exception {
                    return treeService.getTreeHistory(treeId, sessionToken);
                }
            });
        } else {
            map3d.setTrees(new ArrayList<>(cachedTrees));
        }
        workspace.setCenter(map3d);
    }

    /** Opens the 3D map and flies to one tree (used by the registry cards). */
    private void locateIn3D(Tree t) {
        show3DMap();
        Platform.runLater(() -> map3d.focusTree(t.id));
    }

    /** Selection sync from the map back to the registry: filter the inventory to this tree. */
    private void showTreeInRegistry(Tree t) {
        showRegistry();
        statusFilter.setValue("All statuses");
        txtSearch.setText(String.valueOf(t.id));
        searchSuggestionsPopup.hide();
    }

    private final Map<String, ToggleButton> filterChips = new LinkedHashMap<>();
    private ComboBox<String> sortChoice;
    private Integer yearFilter;
    private String lastFilterKey = "";
    private int page = 0;
    private static final int PAGE_SIZE = 12;
    private boolean tableView = false;
    private boolean firstLoadDone = false;
    private TableView<Tree> inventoryTable;
    private final HBox pager = new HBox(6);
    private final HBox activeFilters = new HBox(8);
    private final VBox insightsBox = new VBox();
    private String insightsSignature = "";
    private final Label lastUpdated = new Label("Connecting…");
    private StackPane inventoryHolder;
    private int cardColumns = 2;

    private BorderPane inventory() {
        Label welcome = new Label("Welcome back, " + username + ". Here is the state of the forest today.");
        welcome.getStyleClass().add("muted");
        Circle live = new Circle(4, Color.web("#2fa36f"));
        lastUpdated.setGraphic(live);
        lastUpdated.setGraphicTextGap(6);
        lastUpdated.getStyleClass().add("live-pill");
        HBox titleLine = new HBox(12, RegistryStyle.heading("Forest Registry"), lastUpdated);
        titleLine.setAlignment(Pos.CENTER_LEFT);
        VBox titles = new VBox(4, titleLine, welcome);
        Button open3d = RegistryStyle.secondary("View 3D Forest Map", this::show3DMap);
        open3d.setGraphic(RegistryStyle.navIcon("3D Map"));
        HBox heading = new HBox(12, titles, RegistryStyle.spacer(), open3d,
                RegistryStyle.primary("+  Register Sapling", this::addNewTree));
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.getStyleClass().add("topbar");
        statsWrapper = new BorderPane(); statsWrapper.setTop(createStatsPanel());

        // Insights: live charts built from the registry records; clicking a chart filters the inventory.
        TitledPane insights = new TitledPane("Forest insights", insightsBox);
        insights.getStyleClass().add("insights");
        insights.setExpanded(true);
        insightsSignature = "";
        refreshInsights();

        Label title = new Label("Tree inventory"); title.getStyleClass().add("section-title");
        inventoryCount.getStyleClass().add("muted");
        HBox titleRow = new HBox(10, title, inventoryCount);
        titleRow.setAlignment(Pos.BASELINE_LEFT);

        SVGPath glass = new SVGPath();
        glass.setContent("M7 1a6 6 0 1 0 3.9 10.6l3.8 3.8 1.1-1.1-3.8-3.8A6 6 0 0 0 7 1zm0 1.6a4.4 4.4 0 1 1 0 8.8 4.4 4.4 0 0 1 0-8.8z");
        glass.setStyle("-fx-fill: #7c8a82;");
        glass.setMouseTransparent(true);
        StackPane searchBox = new StackPane(txtSearch, glass);
        StackPane.setAlignment(glass, Pos.CENTER_LEFT);
        StackPane.setMargin(glass, new Insets(0, 0, 0, 12));
        searchBox.setMaxWidth(320);

        sortChoice = new ComboBox<>(FXCollections.observableArrayList(SORTS));
        sortChoice.setValue(SORTS[0]);
        sortChoice.getStyleClass().add("sort-box");
        sortChoice.setTooltip(new Tooltip("Sort the inventory"));
        sortChoice.valueProperty().addListener((o, a, v) -> performSearch());
        Label sortLabel = new Label("Sort");
        sortLabel.getStyleClass().add("muted");

        ToggleGroup views = new ToggleGroup();
        ToggleButton grid = new ToggleButton("Cards");
        ToggleButton table = new ToggleButton("Table");
        grid.setGraphic(RegistryStyle.navIcon("Registry"));
        table.setGraphic(RegistryStyle.navIcon("Table"));
        for (ToggleButton b : List.of(grid, table)) { b.setToggleGroup(views); b.getStyleClass().add("segment"); }
        grid.getStyleClass().add("segment-left");
        table.getStyleClass().add("segment-right");
        (tableView ? table : grid).setSelected(true);
        views.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null) { was.setSelected(true); return; }
            tableView = now == table;
            performSearch();
        });
        HBox toolbar = new HBox(10, searchBox, RegistryStyle.spacer(), sortLabel, sortChoice, new HBox(0, grid, table));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        HBox filters = new HBox(6);
        filters.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup group = new ToggleGroup();
        filterChips.clear();
        for (String choice : statusFilter.getItems()) {
            ToggleButton b = new ToggleButton(choice.equals("All statuses") ? "All" : choice);
            b.setToggleGroup(group); b.getStyleClass().add("chip");
            if (!choice.equals("All statuses")) b.setGraphic(new Circle(4, Color.web(statusAccent(choice))));
            b.setSelected(choice.equals(statusFilter.getValue()));
            b.setOnAction(e -> { b.setSelected(true); statusFilter.setValue(choice); });
            filterChips.put(choice, b);
            filters.getChildren().add(b);
        }
        activeFilters.setAlignment(Pos.CENTER_LEFT);
        filters.getChildren().addAll(RegistryStyle.spacer(), activeFilters);

        cardContainer = new GridPane(); cardContainer.setHgap(14); cardContainer.setVgap(14);
        applyCardColumns();
        inventoryTable = buildInventoryTable();
        inventoryHolder = new StackPane(cardContainer);
        pager.setAlignment(Pos.CENTER);

        VBox body = new VBox(18, statsWrapper, insights, titleRow, toolbar, filters, inventoryHolder, pager);
        body.setPadding(new Insets(24,28,28,28));
        ScrollPane scroll = new ScrollPane(body); scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page-scroll");
        BorderPane panel = new BorderPane(scroll); panel.setTop(heading);
        // Responsive card grid: 1, 2 or 3 columns depending on the available width.
        panel.widthProperty().addListener((o, a, w) -> {
            int cols = w.doubleValue() < 900 ? 1 : w.doubleValue() > 1500 ? 3 : 2;
            if (cols != cardColumns) { cardColumns = cols; applyCardColumns(); performSearch(); }
        });
        performSearch(); return panel;
    }

    private void applyCardColumns() {
        if (cardContainer == null) return;
        cardContainer.getColumnConstraints().clear();
        for (int i = 0; i < cardColumns; i++) {
            ColumnConstraints col = new ColumnConstraints(); col.setPercentWidth(100.0 / cardColumns);
            cardContainer.getColumnConstraints().add(col);
        }
    }

    /** Sortable table view of the filtered inventory (all rows; the table scrolls itself). */
    private TableView<Tree> buildInventoryTable() {
        TableView<Tree> tv = new TableView<>();
        tv.getStyleClass().add("inventory-table");
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.setPlaceholder(new Label("No trees match these filters."));
        tv.setPrefHeight(520);
        TableColumn<Tree, Number> id = new TableColumn<>("ID");
        id.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().id));
        id.setMaxWidth(90);
        TableColumn<Tree, String> species = new TableColumn<>("Species");
        species.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().species));
        TableColumn<Tree, String> status = new TableColumn<>("Health");
        status.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(normalizeStatus(c.getValue().status)));
        status.setComparator(Comparator.comparingInt(TreeDashboard::attentionRank));
        status.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); setText(null); return; }
                Label pill = new Label(v, new Circle(4, Color.web(statusAccent(v))));
                pill.getStyleClass().add("status-pill");
                pill.setStyle("-fx-text-fill: " + statusAccent(v) + "; -fx-background-color: " + statusAccent(v) + "1A;");
                setGraphic(pill); setText(null);
            }
        });
        TableColumn<Tree, Number> height = new TableColumn<>("Height (m)");
        height.setCellValueFactory(c -> new javafx.beans.property.SimpleDoubleProperty(c.getValue().height));
        TableColumn<Tree, Number> diameter = new TableColumn<>("Trunk (cm)");
        diameter.setCellValueFactory(c -> new javafx.beans.property.SimpleDoubleProperty(c.getValue().diameter));
        TableColumn<Tree, String> planted = new TableColumn<>("Planted");
        planted.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().plantedDate));
        TableColumn<Tree, String> age = new TableColumn<>("Age");
        age.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(TreeMap3D.ageText(c.getValue().plantedDate).replace(" since planting", "")));
        age.setSortable(false);
        TableColumn<Tree, String> owner = new TableColumn<>("Recorded by");
        owner.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().ownerUsername));
        TableColumn<Tree, Tree> actions = new TableColumn<>("Actions");
        actions.setSortable(false);
        actions.setMinWidth(230);
        actions.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue()));
        actions.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Tree t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setGraphic(null); return; }
                setGraphic(treeActions(t, true));
            }
        });
        tv.getColumns().addAll(List.of(id, species, status, height, diameter, planted, age, owner, actions));
        tv.setRowFactory(v -> {
            TableRow<Tree> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) openDetails(row.getItem()); });
            row.itemProperty().addListener((o, a, t) -> row.setContextMenu(t == null ? null : treeMenu(t)));
            return row;
        });
        tv.setOnKeyPressed(e -> {
            Tree t = tv.getSelectionModel().getSelectedItem();
            if (t != null && e.getCode() == KeyCode.ENTER) openDetails(t);
        });
        return tv;
    }

    /** View details · View in 3D · Edit · Delete (edit/delete only for records the user may change). */
    private HBox treeActions(Tree t, boolean compact) {
        HBox actions = new HBox(6);
        actions.setAlignment(compact ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
        Button details = new Button("View details");
        details.getStyleClass().add("card-link");
        details.setOnAction(e -> { e.consume(); openDetails(t); });
        actions.getChildren().add(details);
        if (GeoCoordFormat.tryParse(t.location) != null) {
            Button btn3d = new Button("View in 3D");
            btn3d.setGraphic(RegistryStyle.navIcon("3D Map"));
            btn3d.getStyleClass().add("card-link");
            btn3d.setTooltip(new Tooltip("Show this tree on the 3D Forest Map"));
            btn3d.setOnAction(e -> { e.consume(); locateIn3D(t); });
            actions.getChildren().add(btn3d);
        }
        if (canModifyTree(t)) {
            Button btnEdit = createCardIconButton(editActionIcon, "E", RIVER_BLUE, "Edit");
            btnEdit.setOnAction(e -> { e.consume(); editTree(t); });
            Button btnDel = createCardIconButton(deleteActionIcon, "D", DELETE_RED, "Delete");
            btnDel.setOnAction(e -> { e.consume(); deleteTree(t); });
            actions.getChildren().addAll(btnEdit, btnDel);
        }
        return actions;
    }

    /** Right-click menu shared by cards and table rows. */
    private ContextMenu treeMenu(Tree t) {
        MenuItem details = new MenuItem("View details");
        details.setOnAction(e -> openDetails(t));
        MenuItem in3d = new MenuItem("View in 3D map");
        in3d.setOnAction(e -> locateIn3D(t));
        in3d.setDisable(GeoCoordFormat.tryParse(t.location) == null);
        MenuItem copy = new MenuItem("Copy coordinates");
        copy.setOnAction(e -> {
            javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent();
            c.putString(t.location == null ? "" : t.location);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
            Toast.info("Coordinates copied: " + t.location);
        });
        ContextMenu menu = new ContextMenu(details, in3d, copy);
        if (canModifyTree(t)) {
            MenuItem edit = new MenuItem("Edit tree…");
            edit.setOnAction(e -> editTree(t));
            MenuItem del = new MenuItem("Delete tree…");
            del.setOnAction(e -> deleteTree(t));
            menu.getItems().addAll(new SeparatorMenuItem(), edit, del);
        }
        return menu;
    }

    private void openDetails(Tree t) {
        hydrateTreeImageLocal(t);
        new TreeDialogs.TreeDetailDialog(this, t, this::canModifyTree, this::editTree, SDG_GREEN, RIVER_BLUE, PANEL_DARK).showAndWait();
    }

    /** Filter summary with a one-click "Clear filters". */
    private void updateActiveFilters(int shown) {
        activeFilters.getChildren().clear();
        boolean any = yearFilter != null || !"All statuses".equals(statusFilter.getValue())
                || (txtSearch.getText() != null && !txtSearch.getText().isBlank());
        if (yearFilter != null) {
            Button year = new Button("Planted " + yearFilter + "  ✕");
            year.getStyleClass().add("chip-removable");
            year.setOnAction(e -> { yearFilter = null; performSearch(); });
            activeFilters.getChildren().add(year);
        }
        if (any) {
            Label summary = new Label("Showing " + shown + " of " + cachedTrees.size());
            summary.getStyleClass().add("muted");
            Hyperlink clear = new Hyperlink("Clear filters");
            clear.setOnAction(e -> {
                yearFilter = null;
                txtSearch.clear();
                statusFilter.setValue("All statuses");
                performSearch();
            });
            activeFilters.getChildren().addAll(summary, clear);
        }
    }

    /** Page buttons for the card view. */
    private void updatePager(int total) {
        pager.getChildren().clear();
        int pages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        boolean show = !tableView && pages > 1;
        pager.setVisible(show);
        pager.setManaged(show);
        if (!show) return;
        Button prev = new Button("‹ Previous");
        prev.getStyleClass().add("pager-button");
        prev.setDisable(page == 0);
        prev.setOnAction(e -> { page--; performSearch(); });
        pager.getChildren().add(prev);
        for (int i = 0; i < pages; i++) {
            int target = i;
            Button b = new Button(String.valueOf(i + 1));
            b.getStyleClass().add("pager-button");
            if (i == page) b.getStyleClass().add("pager-current");
            b.setOnAction(e -> { page = target; performSearch(); });
            pager.getChildren().add(b);
        }
        Button next = new Button("Next ›");
        next.getStyleClass().add("pager-button");
        next.setDisable(page >= pages - 1);
        next.setOnAction(e -> { page++; performSearch(); });
        int from = page * PAGE_SIZE + 1, to = Math.min(total, (page + 1) * PAGE_SIZE);
        Label range = new Label(from + "–" + to + " of " + total);
        range.getStyleClass().add("muted");
        pager.getChildren().addAll(next, range);
    }

    // ---------------------------------------------------------------- Insights (charts from real records)

    private void refreshInsights() {
        String sig = cachedTrees.stream().map(t -> t.id + ":" + t.status + ":" + t.species + ":" + t.plantedDate)
                .sorted().collect(Collectors.joining(","));
        if (sig.equals(insightsSignature) && !insightsBox.getChildren().isEmpty()) return;
        insightsSignature = sig;
        insightsBox.getChildren().clear();
        if (cachedTrees.isEmpty()) {
            Label none = new Label(firstLoadDone ? "Charts appear once trees are registered." : "Loading insights…");
            none.getStyleClass().add("muted");
            insightsBox.getChildren().add(none);
            return;
        }
        HBox row = new HBox(14, healthChart(), speciesChart(), plantingChart());
        for (Node n : row.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        Label hint = new Label("Tip: click a slice or bar to filter the inventory below.");
        hint.getStyleClass().add("muted");
        insightsBox.setSpacing(8);
        insightsBox.getChildren().addAll(row, hint);
    }

    private VBox chartCard(String title, String subtitle, Node chart) {
        Label t = new Label(title); t.getStyleClass().add("chart-title");
        Label s = new Label(subtitle); s.getStyleClass().add("muted");
        VBox card = new VBox(4, t, s, chart);
        card.getStyleClass().add("chart-card");
        card.setPrefWidth(320);
        VBox.setVgrow(chart, Priority.ALWAYS);
        return card;
    }

    private Node healthChart() {
        PieChart pie = new PieChart();
        pie.setAnimated(false);
        pie.setLabelsVisible(false);
        pie.setLegendVisible(false);
        FlowPane legend = new FlowPane(12, 4);
        legend.setAlignment(Pos.CENTER);
        pie.setStartAngle(90);
        pie.setPrefSize(300, 210);
        pie.getStyleClass().add("health-donut");
        long total = cachedTrees.size();
        for (String st : List.of("Healthy", "Monitoring", "At Risk", "Destroyed")) {
            long n = countByStatus(st);
            if (n == 0) continue;
            PieChart.Data d = new PieChart.Data(st + " (" + n + ")", n);
            pie.getData().add(d);
            String color = statusAccent(st);
            Runnable style = () -> {
                if (d.getNode() == null) return;
                d.getNode().setStyle("-fx-background-color: " + color + "; -fx-border-color: white; -fx-border-width: 2;");
                Tooltip.install(d.getNode(), new Tooltip(st + ": " + n + (n == 1 ? " tree" : " trees") + " (" + percentage(n, total).replace(" of inventory", "") + ")\nClick to filter"));
                d.getNode().setCursor(javafx.scene.Cursor.HAND);
                d.getNode().setOnMouseClicked(e -> { statusFilter.setValue(st); performSearch(); });
            };
            d.nodeProperty().addListener((o, a, node) -> style.run());
            style.run();
            Label key = new Label(st + "  " + n, new Circle(5, Color.web(color)));
            key.getStyleClass().add("legend-key");
            key.setOnMouseClicked(e -> { statusFilter.setValue(st); performSearch(); });
            Tooltip.install(key, new Tooltip("Show " + st.toLowerCase(Locale.ROOT) + " trees"));
            legend.getChildren().add(key);
        }
        pie.setPrefSize(300, 180);
        VBox donut = new VBox(4, pie, legend);
        return chartCard("Health distribution", total + " trees by condition", donut);
    }

    private Node speciesChart() {
        Map<String, Long> counts = cachedTrees.stream()
                .collect(Collectors.groupingBy(t -> t.species == null || t.species.isBlank() ? "Unnamed" : t.species.trim(), Collectors.counting()));
        List<Map.Entry<String, Long>> top = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(6).collect(Collectors.toList());
        CategoryAxis y = new CategoryAxis();
        NumberAxis x = new NumberAxis();
        x.setTickUnit(1); x.setMinorTickVisible(false); x.setForceZeroInRange(true);
        x.setTickLabelFormatter(new javafx.util.StringConverter<>() {
            @Override public String toString(Number n) { return n.doubleValue() == Math.rint(n.doubleValue()) ? String.valueOf(n.intValue()) : ""; }
            @Override public Number fromString(String s) { return Double.parseDouble(s); }
        });
        BarChart<Number, String> chart = new BarChart<>(x, y);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setPrefSize(300, 210);
        chart.getStyleClass().add("species-chart");
        XYChart.Series<Number, String> series = new XYChart.Series<>();
        for (int i = top.size() - 1; i >= 0; i--) {
            Map.Entry<String, Long> e = top.get(i);
            XYChart.Data<Number, String> d = new XYChart.Data<>(e.getValue(), e.getKey());
            series.getData().add(d);
            d.nodeProperty().addListener((o, a, node) -> wireBar(node, e.getKey() + ": " + e.getValue() + (e.getValue() == 1 ? " tree" : " trees") + "\nClick to search",
                    () -> { txtSearch.setText(e.getKey()); searchSuggestionsPopup.hide(); }));
        }
        chart.getData().add(series);
        for (XYChart.Data<Number, String> d : series.getData()) {
            if (d.getNode() != null) {
                String k = d.getYValue();
                wireBar(d.getNode(), k + ": " + d.getXValue() + " trees\nClick to search", () -> { txtSearch.setText(k); searchSuggestionsPopup.hide(); });
            }
        }
        return chartCard("Top species", counts.size() + (counts.size() == 1 ? " species recorded" : " species recorded"), chart);
    }

    private Node plantingChart() {
        java.util.TreeMap<Integer, Long> byYear = new java.util.TreeMap<>();
        for (Tree t : cachedTrees) {
            Integer y = plantedYear(t);
            if (y != null) byYear.merge(y, 1L, Long::sum);
        }
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setTickUnit(1); y.setMinorTickVisible(false);
        y.setTickLabelFormatter(new javafx.util.StringConverter<>() {
            @Override public String toString(Number n) { return n.doubleValue() == Math.rint(n.doubleValue()) ? String.valueOf(n.intValue()) : ""; }
            @Override public Number fromString(String s) { return Double.parseDouble(s); }
        });
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setPrefSize(300, 210);
        chart.getStyleClass().add("planting-chart");
        chart.setCategoryGap(Math.max(14, 140 - 30 * Math.min(4, cachedTrees.stream().map(TreeDashboard::plantedYear).filter(java.util.Objects::nonNull).distinct().count())));
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<Integer, Long> e : byYear.entrySet()) {
            XYChart.Data<String, Number> d = new XYChart.Data<>(String.valueOf(e.getKey()), e.getValue());
            series.getData().add(d);
            int year = e.getKey();
            String tip = year + ": " + e.getValue() + (e.getValue() == 1 ? " tree planted" : " trees planted") + "\nClick to filter";
            d.nodeProperty().addListener((o, a, node) -> wireBar(node, tip, () -> { yearFilter = year; performSearch(); }));
        }
        chart.getData().add(series);
        for (XYChart.Data<String, Number> d : series.getData()) {
            if (d.getNode() != null) {
                int year = Integer.parseInt(d.getXValue());
                wireBar(d.getNode(), year + ": " + d.getYValue() + " planted\nClick to filter", () -> { yearFilter = year; performSearch(); });
            }
        }
        long undated = cachedTrees.stream().filter(t -> plantedYear(t) == null).count();
        return chartCard("Plantings per year", undated == 0 ? "From recorded planting dates" : undated + " without a planting date", chart);
    }

    private static void wireBar(Node node, String tip, Runnable onClick) {
        if (node == null) return;
        Tooltip.install(node, new Tooltip(tip));
        node.setCursor(javafx.scene.Cursor.HAND);
        node.setOnMouseClicked(e -> onClick.run());
    }

    /** Updates the filter chip labels with how many records each status has. */
    private void updateFilterCounts() {
        for (Map.Entry<String, ToggleButton> e : filterChips.entrySet()) {
            String choice = e.getKey();
            long n = choice.equals("All statuses") ? cachedTrees.size() : countByStatus(choice);
            e.getValue().setText((choice.equals("All statuses") ? "All" : choice) + "  " + n);
            e.getValue().setSelected(choice.equals(statusFilter.getValue()));
        }
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
        card.setPadding(new Insets(12));
        ImageView photo = new ImageView();
        StackPane picture = new StackPane();
        picture.setMinSize(96,96); picture.setPrefSize(104,104); picture.setMaxSize(104,104);
        picture.getStyleClass().add("card-photo");
        if (t.imageBytes != null && t.imageBytes.length > 0) {
            Image image = bytesToImage(t.imageBytes);
            if (image != null && !image.isError()) {
                double size = Math.min(image.getWidth(), image.getHeight());
                photo.setViewport(new javafx.geometry.Rectangle2D((image.getWidth()-size)/2, (image.getHeight()-size)/2, size, size));
                photo.setImage(image); photo.setFitWidth(104); photo.setFitHeight(104);
                javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(104, 104);
                clip.setArcWidth(18); clip.setArcHeight(18);
                photo.setClip(clip);
                picture.getChildren().add(photo);
            }
        }
        if (picture.getChildren().isEmpty()) picture.getChildren().add(RegistryStyle.leaf());
        Label species = new Label(t.species); species.getStyleClass().add("card-title");
        String accent = statusAccent(t.status);
        Label condition = new Label(t.status, new Circle(4, Color.web(accent)));
        condition.getStyleClass().add("status-pill");
        condition.setStyle("-fx-text-fill: " + accent + "; -fx-background-color: " + accent + "1A;");
        Label id = new Label("#" + t.id);
        id.getStyleClass().add("id-pill");
        HBox badges = new HBox(6, condition, id);
        badges.setAlignment(Pos.CENTER_LEFT);
        Label measurements = new Label(fmtNumber(t.height) + " m tall  ·  " + fmtNumber(t.diameter) + " cm trunk");
        measurements.getStyleClass().add("card-measure");
        String planted = t.plantedDate == null || t.plantedDate.isBlank() ? "Planting date unknown" : "Planted " + t.plantedDate;
        Label meta = new Label(planted + "  ·  " + (t.location == null ? "No location" : t.location));
        meta.setWrapText(true);
        meta.getStyleClass().add("muted");
        VBox info = new VBox(6, species, badges, measurements, meta);
        info.setPadding(new Insets(2,0,2,14));
        card.setLeft(picture); card.setCenter(info);

        HBox actions = treeActions(t, false);
        actions.setPadding(new Insets(6, 0, 0, 0));
        card.setBottom(actions);
        // Click or Enter opens the record; right-click offers every action; a short press animation confirms.
        card.setFocusTraversable(true);
        card.setOnMouseClicked(e -> {
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            card.requestFocus();
            javafx.animation.ScaleTransition press = new javafx.animation.ScaleTransition(Duration.millis(90), card);
            press.setToX(0.985); press.setToY(0.985); press.setAutoReverse(true); press.setCycleCount(2);
            press.setOnFinished(ev -> openDetails(t));
            press.play();
        });
        card.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) openDetails(t); });
        card.setOnContextMenuRequested(e -> { treeMenu(t).show(card, e.getScreenX(), e.getScreenY()); e.consume(); });
        Tooltip.install(card, new Tooltip("Click for details · right-click for more actions"));
        int index = cardContainer.getChildren().size();
        cardContainer.add(card, index % cardColumns, index / cardColumns);
    }

    private static String fmtNumber(double v) {
        if (v == Math.rint(v)) return String.format(Locale.US, "%.0f", v);
        return String.format(Locale.US, "%.2f", v).replaceAll("0+$", "");
    }

    private HBox createStatsPanel() {
        HBox panel = new HBox(12);
        panel.setAlignment(Pos.CENTER_LEFT);
        panel.setPadding(new Insets(0, 0, 5, 0));
        long total = cachedTrees.size();
        long healthy = countByStatus("Healthy");
        long monitoring = countByStatus("Monitoring");
        long atRisk = countByStatus("At Risk");
        long destroyed = countByStatus("Destroyed");
        panel.getChildren().addAll(
                createStatBox("Total trees", total, total, "#20865B", "In the registry", "All statuses"),
                createStatBox("Healthy", healthy, total, "#4F772D", percentage(healthy, total), "Healthy"),
                createStatBox("Monitoring", monitoring, total, RIVER_BLUE, percentage(monitoring, total), "Monitoring"),
                createStatBox("At Risk", atRisk, total, "#BC6C25", percentage(atRisk, total), "At Risk"),
                createStatBox("Destroyed", destroyed, total, DELETE_RED, percentage(destroyed, total), "Destroyed")
        );
        if (isAdmin) onlineUsers.setText("Active users · " + activeUsersCount);
        return panel;
    }

    private static String percentage(long count, long total) {
        return (total == 0 ? 0 : Math.round(100.0 * count / total)) + "% of inventory";
    }

    private VBox createStatBox(String title, long value, long total, String accent, String summary, String filter) {
        VBox box = new VBox(6);
        // Each tile is a shortcut: click to show exactly those records in the inventory.
        box.setOnMouseClicked(e -> { statusFilter.setValue(filter); performSearch(); });
        box.setFocusTraversable(true);
        box.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) { statusFilter.setValue(filter); performSearch(); } });
        if (filter.equals(statusFilter.getValue())) box.getStyleClass().add("stat-active");
        Tooltip.install(box, new Tooltip(filter.equals("All statuses") ? "Show all trees" : "Show " + title.toLowerCase(Locale.ROOT) + " trees"));
        box.setPrefWidth(180);
        HBox.setHgrow(box, Priority.ALWAYS);
        box.getStyleClass().add("stat");
        Circle dot = new Circle(5, Color.web(accent));
        Label heading = new Label(title, dot);
        heading.setGraphicTextGap(8);
        heading.getStyleClass().add("stat-title");
        Label count = new Label(String.valueOf(value));
        count.getStyleClass().add("stat-value");
        // Share of inventory as a slim bar in the status colour.
        ProgressBar share = new ProgressBar(total == 0 ? 0 : (double) value / total);
        share.setMaxWidth(Double.MAX_VALUE);
        share.setPrefHeight(6);
        share.getStyleClass().add("stat-bar");
        share.setStyle("-fx-accent: " + accent + ";");
        Label detail = new Label(summary);
        detail.getStyleClass().add("muted");
        box.getChildren().addAll(heading, count, share, detail);
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
                    firstLoadDone = true;
                    lastUpdated.setText("Live · updated " + java.time.LocalTime.now().withNano(0));
                    reconnect.setVisible(false);
                    reconnect.setManaged(false);
                    cachedTrees.clear(); cachedTrees.addAll(newTrees);
                    if (map3d != null) map3d.setTrees(new ArrayList<>(cachedTrees));
                    performSearch();
                    refreshSearchSuggestions();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> { connection.setText("Server unavailable"); reconnect.setManaged(true); reconnect.setVisible(true);
                    lastUpdated.setText("Offline · showing last data"); });
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
        if (cardContainer == null) return;
        cardContainer.getChildren().clear();
        inventoryCount.setText(list.size() + (list.size() == 1 ? " record" : " records"));
        if (tableView && inventoryTable != null) {
            inventoryHolder.getChildren().setAll(inventoryTable);
            inventoryTable.getItems().setAll(list);
            inventoryTable.sort();
        } else {
            if (inventoryHolder != null) inventoryHolder.getChildren().setAll(cardContainer);
            int pages = Math.max(1, (int) Math.ceil(list.size() / (double) PAGE_SIZE));
            page = Math.max(0, Math.min(page, pages - 1));
            for (Tree t : list.subList(page * PAGE_SIZE, Math.min(list.size(), (page + 1) * PAGE_SIZE))) addTreeCard(t);
        }
        if (!firstLoadDone && cachedTrees.isEmpty()) {
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setMaxSize(36, 36);
            Label loading = new Label("Loading the inventory from the server…");
            loading.getStyleClass().add("muted");
            VBox box = new VBox(10, spinner, loading);
            box.setAlignment(Pos.CENTER);
            box.getStyleClass().add("empty-state");
            cardContainer.add(box, 0, 0, cardColumns, 1);
        } else if (list.isEmpty() && !tableView) {
            Label none = new Label(cachedTrees.isEmpty() ? "No trees registered yet" : "No trees match these filters");
            none.getStyleClass().add("section-title");
            Label hint = new Label(cachedTrees.isEmpty() ? "Register a sapling to start the inventory."
                    : "Try another species or ID, or clear the filters.");
            hint.getStyleClass().add("muted");
            Button action = cachedTrees.isEmpty() ? RegistryStyle.primary("+  Register Sapling", this::addNewTree)
                    : RegistryStyle.secondary("Clear filters", () -> { yearFilter = null; txtSearch.clear(); statusFilter.setValue("All statuses"); performSearch(); });
            VBox empty = new VBox(8, RegistryStyle.leaf(), none, hint, action);
            empty.setAlignment(Pos.CENTER);
            empty.getStyleClass().add("empty-state");
            cardContainer.add(empty, 0, 0, cardColumns, 1);
        }
        updateFilterCounts();
        updateActiveFilters(list.size());
        updatePager(list.size());
        if (statsWrapper != null) statsWrapper.setTop(createStatsPanel());
        refreshInsights();
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
                            Toast.show(this, "admin".equals(username)
                                    ? t.species + " added to the inventory. " + resp.replace("SUCCESS:", "").trim()
                                    : t.species + " submitted. It will appear once an administrator approves it.", Toast.Kind.SUCCESS);
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
                    Platform.runLater(() -> Toast.show(this, updated.species + " #" + updated.id + " saved.", Toast.Kind.SUCCESS));
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
                            Toast.show(this, t.species + " #" + t.id + " moved to the archive.", Toast.Kind.SUCCESS);
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
