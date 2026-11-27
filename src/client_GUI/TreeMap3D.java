package client_GUI;

import Model.Tree;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
import javafx.geometry.Pos;
import javafx.scene.AmbientLight;
import javafx.scene.Cursor;
import javafx.scene.DirectionalLight;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Lorax 3D Forest Map.
 * <p>
 * A native JavaFX 3D (GPU) view of the registry. The ground is the OpenStreetMap map of Baguio (the same tiles
 * the ranger pin picker uses), and every registry tree with a {@code lat,lon} location is drawn as a procedural
 * 3D tree standing at that position. Tiles and trees share one coordinate system ({@link GeoProjection},
 * Web Mercator in local metres), so each tree sits exactly where it was recorded.
 * </p>
 * <p>
 * The view reads the dashboard's live tree list ({@link #setTrees}); it never writes to the database itself.
 * Edits go through the dashboard's existing dialogs and RMI calls via {@link Host}, so registry rules
 * (ownership, admin review, activity logging) stay exactly as they were.
 * </p>
 * Navigation: left-drag pans, right-drag (or Ctrl/Shift + drag) rotates and tilts, scroll zooms,
 * double-click flies to a point, arrow keys pan, Q/E rotate, +/- zoom.
 */
public final class TreeMap3D extends StackPane {

    /** Callbacks into the dashboard, so map actions reuse the existing registry workflows. */
    public interface Host {
        boolean canEdit(Tree t);
        void openRecord(Tree t);
        void editRecord(Tree t);
        void showInRegistry(Tree t);
        /** Called on a background thread. Returns monitoring history lines, newest first. */
        List<String> loadHistory(int treeId) throws Exception;
    }

    private static final Color ACCENT = Color.web("#20865B");
    private static final int MAX_DETAIL_TILES = 64;
    private static final int MAX_CONTEXT_TILES = 49;
    private static final double MIN_DIST = 6, MAX_DIST = 15000;

    private final Host host;
    private final Map<Integer, Tree> trees = new LinkedHashMap<>();
    private final Map<Integer, TreeNode> nodes = new HashMap<>();

    // ---- 3D scene
    private final Group world = new Group();
    private final Region sky = new Region();
    /** The 3D viewport (below the header); overlays float inside it. */
    private final StackPane mapArea = new StackPane();
    private final Group baseGroup = new Group();
    private final Group contextTiles = new Group();
    private final Group detailTiles = new Group();
    private final Group lodTiles = new Group();
    private final Map<String, MeshView> lodCache = new LinkedHashMap<>();
    private final javafx.animation.PauseTransition lodDebounce = new javafx.animation.PauseTransition(Duration.millis(350));
    private int zDetail = 17;
    private final Group buildingsGroup = new Group();
    private Terrain terrain = Terrain.flat();
    private boolean terrainOn = true;
    private boolean buildingsOn = true;
    private OsmDataSource.Basemap basemap = OsmDataSource.Basemap.STANDARD;
    private int buildingCount = -1;
    private boolean terrainLoading;
    private final Label scaleText = new Label();
    private final Region scaleBar = new Region();
    private final Label attribution = new Label();
    /** Default camera heading: looking south-southeast across the city. */
    private static final double HOME_YAW = 156;
    private final Group treesGroup = new Group();
    private final Group pinsGroup = new Group();
    private final Group selectionGroup = new Group();
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final SubScene subScene;
    private final DoubleProperty targetX = new SimpleDoubleProperty(0);
    private final DoubleProperty targetZ = new SimpleDoubleProperty(0);
    private final DoubleProperty yaw = new SimpleDoubleProperty(0);
    private final DoubleProperty pitch = new SimpleDoubleProperty(55);
    private final DoubleProperty distance = new SimpleDoubleProperty(900);
    private Timeline flight;
    private boolean cameraDirty;

    // ---- geography
    private GeoProjection proj;
    private double[] area; // south, west, north, east of the detailed area
    private int generation; // bumps on every area rebuild; stale background results are dropped
    private final ExecutorService tilePool = Executors.newFixedThreadPool(2, daemon("TreeMap3D-tiles"));
    private final ExecutorService dataPool = Executors.newSingleThreadExecutor(daemon("TreeMap3D-data"));
    private final AtomicInteger tilesLoaded = new AtomicInteger();
    private int tilesTotal;
    private boolean offline = ClientConfig.useOfflineMapTiles();

    // ---- trees / selection
    private double treeScale = 6;
    private int selectedId = -1;
    private int hoverId = -1;
    private final Group halo = new Group();
    private ScaleTransition haloPulse;
    private Cylinder beam;

    // ---- overlay UI
    private final Pane labelLayer = new Pane();
    private final Map<Integer, Label> tags = new HashMap<>();
    private boolean showTags = true;
    private final Label hoverTip = new Label();
    private final Label statusLabel = new Label();
    private final ObservableList<Tree> listItems = FXCollections.observableArrayList();
    private final ListView<Tree> treeList = new ListView<>(listItems);
    private final TextField search = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final Label countLabel = new Label();
    private final VBox infoPanel = new VBox(10);
    private final ScrollPane infoScroll = new ScrollPane(infoPanel);
    private final SVGPath compassNeedle = new SVGPath();
    private final Label emptyNotice = new Label();
    private final Label offlineNotice = new Label();
    private boolean syncingList;

    public TreeMap3D(Collection<Tree> initialTrees, Host host) {
        this.host = host;
        getStyleClass().add("tree-map");
        var css = TreeMap3D.class.getResource("/res/map3d.css");
        if (css != null) getStylesheets().add(css.toExternalForm());

        // Lights: soft sky ambient + low tropical sun, so the trees read as solid 3D shapes.
        AmbientLight ambient = new AmbientLight(Color.gray(0.52));
        DirectionalLight sun = new DirectionalLight(Color.gray(0.5));
        sun.setDirection(new Point3D(-0.45, 1.0, 0.55));
        DirectionalLight fill = new DirectionalLight(Color.gray(0.26));
        fill.setDirection(new Point3D(0.6, 0.4, -0.7));

        world.getChildren().addAll(baseGroup, contextTiles, detailTiles, lodTiles, buildingsGroup, treesGroup, selectionGroup, pinsGroup,
                ambient, sun, fill);

        camera.setFieldOfView(42);
        // Multisampling can be switched off (-Dlorax.map3d.antialiasing=false) on GPUs/drivers that render it badly.
        boolean aa = !"false".equalsIgnoreCase(System.getProperty("lorax.map3d.antialiasing", "true"));
        subScene = new SubScene(world, 800, 600, true, aa ? SceneAntialiasing.BALANCED : SceneAntialiasing.DISABLED);
        subScene.setCamera(camera);
        // The sky gradient is a 2D layer behind a transparent SubScene: a gradient *fill* on a depth-buffered
        // SubScene corrupts depth testing on some OpenGL drivers (parts of trees and rings vanish).
        subScene.setFill(Color.TRANSPARENT);
        sky.setBackground(new javafx.scene.layout.Background(new javafx.scene.layout.BackgroundFill(
                new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#79B2E6")), new Stop(0.42, Color.web("#B4D3EF")),
                        new Stop(0.7, Color.web("#E4EEF8")), new Stop(1, Color.web("#DFEAF4"))),
                null, null)));
        sky.setMouseTransparent(true);
        subScene.widthProperty().bind(mapArea.widthProperty());
        subScene.heightProperty().bind(mapArea.heightProperty());
        mapArea.setMinSize(0, 0);
        // The SubScene must not dictate the viewport's size, or the layout could never shrink.
        subScene.setManaged(false);
        subScene.setFocusTraversable(true);

        for (DoubleProperty p : List.of(targetX, targetZ, yaw, pitch, distance)) {
            p.addListener((o, a, b) -> requestCameraUpdate());
        }
        installNavigation();
        buildSelectionVisuals();

        labelLayer.setPickOnBounds(false);
        labelLayer.setMouseTransparent(true);
        hoverTip.getStyleClass().add("tm-tooltip");
        hoverTip.setVisible(false);
        hoverTip.setMouseTransparent(true);
        hoverTip.setManaged(false);
        labelLayer.getChildren().add(hoverTip);

        StackPane chrome = buildChrome();
        labelLayer.setManaged(false);
        labelLayer.prefWidthProperty().bind(mapArea.widthProperty());
        labelLayer.prefHeightProperty().bind(mapArea.heightProperty());
        labelLayer.resizeRelocate(0, 0, 0, 0);
        mapArea.widthProperty().addListener((o, a, b) -> labelLayer.resize(mapArea.getWidth(), mapArea.getHeight()));
        mapArea.heightProperty().addListener((o, a, b) -> labelLayer.resize(mapArea.getWidth(), mapArea.getHeight()));
        mapArea.getChildren().addAll(sky, subScene, labelLayer, chrome);
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(mapArea.widthProperty());
        clip.heightProperty().bind(mapArea.heightProperty());
        mapArea.setClip(clip);
        BorderPane frame = new BorderPane(mapArea);
        frame.setTop(buildTopBar());
        getChildren().add(frame);
        setMinSize(400, 300);

        setTrees(initialTrees, true);
        // Keep 2D tags glued to their 3D trees as the window resizes.
        mapArea.widthProperty().addListener((o, a, b) -> requestCameraUpdate());
        mapArea.heightProperty().addListener((o, a, b) -> requestCameraUpdate());
        sceneProperty().addListener((o, a, b) -> requestCameraUpdate());
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return r -> { Thread t = new Thread(r, name); t.setDaemon(true); return t; };
    }

    // =====================================================================================================
    // Public API used by the dashboard
    // =====================================================================================================

    /** Replaces the tree set (called on every registry refresh). Unchanged trees keep their 3D nodes. */
    public void setTrees(Collection<Tree> latest) {
        setTrees(latest, false);
    }

    /** Selects a tree, highlights it and flies the camera to it. */
    public void focusTree(int id) {
        Tree t = trees.get(id);
        if (t == null) return;
        if (!filterMatches(t)) {
            search.setText("");
            statusFilter.setValue("All statuses");
        }
        select(id, true);
    }

    public int getSelectedTreeId() {
        return selectedId;
    }

    /** Number of registry trees currently drawn as 3D models (used by verification). */
    public int getRenderedTreeCount() {
        return nodes.size();
    }

    /** Status the 3D map is currently showing for a tree, or null when it is not on the map (verification). */
    public String getStatusShown(int id) {
        TreeNode n = nodes.get(id);
        return n == null ? null : n.tree.status;
    }

    /** Scene x/z (metres east/north of the map origin) of a tree's 3D model, or null (verification). */
    public double[] getScenePosition(int id) {
        TreeNode n = nodes.get(id);
        return n == null ? null : new double[]{n.root.getTranslateX(), n.root.getTranslateZ()};
    }

    /** The same projection applied to a lat/lon, to check trees sit at their real coordinates (verification). */
    public double[] project(double lat, double lon) {
        return proj == null ? null : new double[]{proj.x(lon), proj.z(lat)};
    }

    /** Stops background work; call when the dashboard closes. */
    public void dispose() {
        tilePool.shutdownNow();
        dataPool.shutdownNow();
        if (flight != null) flight.stop();
        if (haloPulse != null) haloPulse.stop();
    }

    // =====================================================================================================
    // Trees
    // =====================================================================================================

    private void setTrees(Collection<Tree> latest, boolean first) {
        Map<Integer, Tree> incoming = new LinkedHashMap<>();
        for (Tree t : latest) {
            if (t != null && GeoCoordFormat.tryParse(t.location) != null) incoming.put(t.id, t);
        }
        Map<Integer, Tree> previous = new HashMap<>(trees);
        trees.clear();
        trees.putAll(incoming);

        boolean rebuildArea = first || area == null || outsideArea(incoming.values());
        boolean anyChange = rebuildArea || previous.size() != incoming.size();
        if (!anyChange) {
            for (Tree t : incoming.values()) {
                Tree old = previous.get(t.id);
                if (old == null || !signature(old).equals(signature(t))) { anyChange = true; break; }
            }
        }
        if (!anyChange) {
            // Periodic refresh with identical data: keep nodes, list and info panel exactly as they are.
            for (Tree t : incoming.values()) {
                TreeNode n = nodes.get(t.id);
                if (n != null) n.tree = t;
            }
            return;
        }
        if (rebuildArea) {
            rebuildArea();
            if (first) fitAll(false);
        } else {
            for (Integer id : new ArrayList<>(nodes.keySet())) {
                if (!incoming.containsKey(id)) removeNode(id);
            }
            for (Tree t : incoming.values()) {
                Tree old = previous.get(t.id);
                TreeNode n = nodes.get(t.id);
                if (n == null || old == null || !signature(old).equals(signature(t))) {
                    if (n != null) removeNode(t.id);
                    addNode(t);
                } else {
                    n.tree = t;
                }
            }
        }
        if (selectedId >= 0 && !trees.containsKey(selectedId)) clearSelection();
        refreshList();
        if (selectedId >= 0) {
            Tree before = previous.get(selectedId);
            Tree now = trees.get(selectedId);
            boolean changed = before == null || !signature(before).equals(signature(now));
            showInfo(now, changed);
            positionSelectionVisuals();
        }
        emptyNotice.setVisible(trees.isEmpty());
        updateStatus();
        requestCameraUpdate();
    }

    private static String signature(Tree t) {
        return t.id + "|" + t.species + "|" + t.location + "|" + t.height + "|" + t.diameter + "|" + t.status + "|"
                + t.plantedDate + "|" + t.notes + "|" + t.ownerUsername + "|" + (t.imageBytes == null ? 0 : t.imageBytes.length);
    }

    private boolean outsideArea(Collection<Tree> ts) {
        if (area == null) return true;
        for (Tree t : ts) {
            double[] ll = GeoCoordFormat.tryParse(t.location);
            if (ll[0] < area[0] || ll[0] > area[2] || ll[1] < area[1] || ll[1] > area[3]) return true;
        }
        return false;
    }

    private final class TreeNode {
        Tree tree;
        final Group root = new Group();   // positioned at the tree's ground location
        final Group model;                // procedural tree, scaled by the exaggeration factor
        final Scale modelScale = new Scale(1, 1, 1);
        final MeshView ring;
        final Group pin;
        final Scale pinScale = new Scale(1, 1, 1);
        final Translate pinLift = new Translate();
        final double height;
        final double crown;
        double x, z, baseY;

        TreeNode(Tree t) {
            tree = t;
            height = Tree3DModel.modelHeight(t);
            crown = Tree3DModel.crownRadius(t);
            Tree3DModel.Health h = Tree3DModel.health(t.status);
            model = Tree3DModel.build(t);
            model.getTransforms().add(modelScale);
            ring = Tree3DModel.ring(crown * 0.95, crown * 1.18, Tree3DModel.healthColor(h), 0.05);
            ring.getTransforms().add(modelScale);
            // Map overlays stay visible on slopes and through buildings, like markers on a web map.
            ring.setDepthTest(javafx.scene.DepthTest.DISABLE);
            root.getChildren().addAll(ring, model);
            pin = Tree3DModel.beacon(Tree3DModel.healthColor(h));
            pin.getTransforms().addAll(pinLift, pinScale);
            pin.setDepthTest(javafx.scene.DepthTest.DISABLE);
            tagAll(root, t.id);
            tagAll(pin, t.id);
        }

        void place() {
            double[] ll = GeoCoordFormat.tryParse(tree.location);
            x = proj.x(ll[1]);
            z = proj.z(ll[0]);
            baseY = -terrain.height(ll[0], ll[1]); // stand on the ground (negative y is up)
            root.setTranslateX(x);
            root.setTranslateY(baseY);
            root.setTranslateZ(z);
            pin.setTranslateX(x);
            pin.setTranslateY(baseY);
            pin.setTranslateZ(z);
            applyScale();
        }

        void applyScale() {
            modelScale.setX(treeScale); modelScale.setY(treeScale); modelScale.setZ(treeScale);
        }

        double topY() {
            return height * treeScale;
        }
    }

    private static void tagAll(Node n, int id) {
        n.setUserData(id);
        if (n instanceof javafx.scene.Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) tagAll(c, id);
        }
    }

    private void addNode(Tree t) {
        TreeNode n = new TreeNode(t);
        n.place();
        nodes.put(t.id, n);
        treesGroup.getChildren().add(n.root);
        pinsGroup.getChildren().add(n.pin);
        Label tag = new Label();
        tag.getStyleClass().add("tm-tag");
        tag.setManaged(false);
        tag.setMouseTransparent(true);
        tags.put(t.id, tag);
        labelLayer.getChildren().add(0, tag);
        updateTag(t.id);
        applyFilterVisibility(n);
    }

    private void removeNode(int id) {
        TreeNode n = nodes.remove(id);
        if (n != null) {
            treesGroup.getChildren().remove(n.root);
            pinsGroup.getChildren().remove(n.pin);
        }
        Label tag = tags.remove(id);
        if (tag != null) labelLayer.getChildren().remove(tag);
    }

    private void updateTag(int id) {
        Label tag = tags.get(id);
        Tree t = trees.get(id);
        if (tag == null || t == null) return;
        tag.setText(safe(t.species, "Tree") + "  #" + t.id);
        tag.setStyle("-fx-border-color: " + web(Tree3DModel.healthColor(Tree3DModel.health(t.status))) + ";");
    }

    private void rebuildAllTreeNodes() {
        for (Integer id : new ArrayList<>(nodes.keySet())) removeNode(id);
        for (Tree t : trees.values()) addNode(t);
    }

    // =====================================================================================================
    // Area and OSM ground tiles
    // =====================================================================================================

    private void rebuildArea() {
        generation++;
        int gen = generation;
        double s = 90, w = 180, n = -90, e = -180;
        for (Tree t : trees.values()) {
            double[] ll = GeoCoordFormat.tryParse(t.location);
            s = Math.min(s, ll[0]); n = Math.max(n, ll[0]);
            w = Math.min(w, ll[1]); e = Math.max(e, ll[1]);
        }
        if (trees.isEmpty()) {
            s = n = ClientConfig.getMapDefaultLat();
            w = e = ClientConfig.getMapDefaultLon();
        }
        double cLat = (s + n) / 2, cLon = (w + e) / 2;
        proj = new GeoProjection(cLat, cLon);
        // Margin: at least ~450 m, or 30% of the spread, around the trees.
        double spanM = Math.max(GeoProjection.distanceMeters(s, w, n, w), GeoProjection.distanceMeters(s, w, s, e));
        double marginM = Math.max(450, spanM * 0.3);
        double dLat = marginM / 111320.0;
        double dLon = marginM / (111320.0 * Math.cos(Math.toRadians(cLat)));
        area = new double[]{s - dLat, w - dLon, n + dLat, e + dLon};

        detailTiles.getChildren().clear();
        contextTiles.getChildren().clear();
        baseGroup.getChildren().clear();
        buildingsGroup.getChildren().clear();
        lodTiles.getChildren().clear();
        lodCache.clear();
        tilesLoaded.set(0);
        tilesTotal = 0;
        buildingCount = -1;
        terrain = Terrain.flat();

        // Haze-coloured ground far below, so the city fades into the horizon like the sky's fog.
        baseGroup.getChildren().add(quad(-60000, -60000, 60000, 60000, 400.0, solid(Color.web("#DCE7EF"))));
        rebuildAllTreeNodes();

        // Terrain first (trees, tiles and buildings all sit on it), then the ground imagery and buildings.
        double[] ctx = expand(area, 2.6);
        terrainLoading = terrainOn;
        updateStatus();
        GeoProjection p = proj;
        boolean off = offline, useTerrain = terrainOn;
        dataPool.execute(() -> {
            Terrain t = useTerrain ? Terrain.load(ctx[0], ctx[1], ctx[2], ctx[3], p.originLat(), p.originLon(), off) : Terrain.flat();
            Platform.runLater(() -> {
                if (gen != generation) return;
                terrain = t;
                terrainLoading = false;
                buildGround(gen, ctx);
                for (TreeNode tn : nodes.values()) tn.place();
                loadBuildings(gen);
                updateStatus();
                requestCameraUpdate();
            });
        });
    }

    private void buildGround(int gen, double[] ctx) {
        zDetail = chooseZoom(area, MAX_DETAIL_TILES, 18);
        addTileLayer(detailTiles, area, zDetail, 0.0, gen, 16, null);
        // Wider, coarser context ring so the city continues to the horizon (tiles under the detail area are skipped).
        int zCtx = Math.min(zDetail - 2, chooseZoom(ctx, MAX_CONTEXT_TILES, zDetail - 2));
        if (zCtx >= 10) addTileLayer(contextTiles, ctx, zCtx, 2.5, gen, 32, area);
    }

    private static double[] expand(double[] a, double factor) {
        double cLat = (a[0] + a[2]) / 2, cLon = (a[1] + a[3]) / 2;
        double hLat = (a[2] - a[0]) / 2 * factor, hLon = (a[3] - a[1]) / 2 * factor;
        return new double[]{cLat - hLat, cLon - hLon, cLat + hLat, cLon + hLon};
    }

    private static int chooseZoom(double[] a, int maxTiles, int maxZoom) {
        for (int z = Math.min(18, maxZoom); z >= 3; z--) {
            int x0 = (int) Math.floor(GeoProjection.tileXf(a[1], z)), x1 = (int) Math.floor(GeoProjection.tileXf(a[3], z));
            int y0 = (int) Math.floor(GeoProjection.tileYf(a[2], z)), y1 = (int) Math.floor(GeoProjection.tileYf(a[0], z));
            if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) <= maxTiles) return z;
        }
        return 3;
    }

    private void addTileLayer(Group layer, double[] a, int z, double depth, int gen, int segs, double[] skipInside) {
        int x0 = (int) Math.floor(GeoProjection.tileXf(a[1], z)), x1 = (int) Math.floor(GeoProjection.tileXf(a[3], z));
        int y0 = (int) Math.floor(GeoProjection.tileYf(a[2], z)), y1 = (int) Math.floor(GeoProjection.tileYf(a[0], z));
        // Load tiles nearest the centre first.
        List<int[]> order = new ArrayList<>();
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) order.add(new int[]{x, y});
        double cx = (x0 + x1) / 2.0, cy = (y0 + y1) / 2.0;
        order.sort(Comparator.comparingDouble(p -> Math.hypot(p[0] - cx, p[1] - cy)));
        for (int[] p : order) {
            int tx = p[0], ty = p[1];
            double west = GeoProjection.tileLon(tx, z), east = GeoProjection.tileLon(tx + 1, z);
            double north = GeoProjection.tileLat(ty, z), south = GeoProjection.tileLat(ty + 1, z);
            if (skipInside != null && south >= skipInside[0] && north <= skipInside[2] && west >= skipInside[1] && east <= skipInside[3]) continue;
            MeshView tile = terrainPatch(west, south, east, north, depth, segs, placeholderMaterial((tx + ty) % 2 == 0));
            layer.getChildren().add(tile);
            tilesTotal++;
            OsmDataSource.Basemap style = basemap;
            tilePool.execute(() -> {
                byte[] png = OsmDataSource.tile(style, z, tx, ty, offline);
                Image img = null;
                if (png != null) {
                    Image decoded = new Image(new ByteArrayInputStream(png));
                    if (!decoded.isError()) img = decoded;
                }
                Image finalImg = img;
                Platform.runLater(() -> {
                    if (gen != generation) return;
                    tilesLoaded.incrementAndGet();
                    if (finalImg != null) tile.setMaterial(tileMaterial(finalImg));
                    updateStatus();
                });
            });
        }
    }

    /**
     * One map tile draped over the terrain: a (segs+1)² grid whose vertices follow the ground height, textured
     * north-up. Faces are wound so normals point up, letting the sun shade slopes like a hillshade.
     */
    private MeshView terrainPatch(double west, double south, double east, double north, double depth, int segs, PhongMaterial mat) {
        double x0 = proj.x(west), x1 = proj.x(east), zN = proj.z(north), zS = proj.z(south);
        MeshBuilder mb = new MeshBuilder();
        for (int j = 0; j <= segs; j++) {
            double zz = zN + (zS - zN) * j / segs;
            double lat = proj.latAt(zz);
            for (int i = 0; i <= segs; i++) {
                double xx = x0 + (x1 - x0) * i / segs;
                float y = (float) (-terrain.height(lat, proj.lonAt(xx)) + depth);
                mb.point((float) xx, y, (float) zz, (float) i / segs, (float) j / segs);
            }
        }
        int row = segs + 1;
        for (int j = 0; j < segs; j++) {
            for (int i = 0; i < segs; i++) {
                int a = j * row + i, b = a + 1, c = a + row + 1, d = a + row;
                mb.face(a, c, b);
                mb.face(a, d, c);
            }
        }
        MeshView v = new MeshView(mb.build());
        v.setMaterial(mat);
        v.setCullFace(CullFace.NONE);
        return v;
    }

    /** Horizontal rectangle from (x0, z0) to (x1, z1) at the given depth below ground, textured north-up. */
    private static MeshView quad(double x0, double z0, double x1, double z1, double depth, PhongMaterial mat) {
        TriangleMesh m = new TriangleMesh();
        float y = (float) depth;
        m.getPoints().addAll(
                (float) x0, y, (float) z1,   // 0: north-west
                (float) x1, y, (float) z1,   // 1: north-east
                (float) x1, y, (float) z0,   // 2: south-east
                (float) x0, y, (float) z0);  // 3: south-west
        m.getTexCoords().addAll(0, 0, 1, 0, 1, 1, 0, 1);
        m.getFaces().addAll(0, 0, 1, 1, 2, 2, 0, 0, 2, 2, 3, 3);
        MeshView v = new MeshView(m);
        v.setMaterial(mat);
        v.setCullFace(CullFace.NONE);
        return v;
    }

    /** Map imagery lit by the sun, so hills get a natural hillshade while flat ground keeps its colours. */
    private static PhongMaterial tileMaterial(Image img) {
        PhongMaterial m = new PhongMaterial(Color.WHITE);
        m.setDiffuseMap(img);
        m.setSpecularColor(Color.BLACK);
        return m;
    }

    /** Ground shown before (or without) imagery, lit like the map tiles so the terrain still reads. */
    private PhongMaterial placeholderMaterial(boolean even) {
        PhongMaterial m = new PhongMaterial(even ? Color.web("#EEF2EC") : Color.web("#E8EDE6"));
        m.setSpecularColor(Color.BLACK);
        return m;
    }

    private static PhongMaterial flatGround() {
        return solid(Color.web("#DCE6DF"));
    }

    /** A flat, unlit colour (self-illuminated 1×1 texture), so ground colours look the same as in the 2D app. */
    private static PhongMaterial solid(Color c) {
        javafx.scene.image.WritableImage px = new javafx.scene.image.WritableImage(1, 1);
        px.getPixelWriter().setColor(0, 0, c);
        PhongMaterial m = new PhongMaterial(Color.BLACK);
        m.setSelfIlluminationMap(px);
        m.setSpecularColor(Color.BLACK);
        return m;
    }

    private void loadBuildings(int gen) {
        if (!buildingsOn) { buildingCount = 0; updateStatus(); return; }
        double latSpan = GeoProjection.distanceMeters(area[0], area[1], area[2], area[1]);
        double lonSpan = GeoProjection.distanceMeters(area[0], area[1], area[0], area[3]);
        if (latSpan > 7000 || lonSpan > 7000) { buildingCount = 0; updateStatus(); return; }
        double[] a = area.clone();
        GeoProjection p = proj;
        Terrain t = terrain;
        boolean off = offline;
        dataPool.execute(() -> {
            String json = OsmDataSource.buildingsJson(a[0], a[1], a[2], a[3], off);
            List<OsmBuildings.Footprint> fps = OsmBuildings.parse(json, p, 20000);
            OsmBuildings.Meshes meshes = OsmBuildings.buildMeshes(fps,
                    (x, z) -> -t.height(p.latAt(z), p.lonAt(x)));
            Platform.runLater(() -> {
                if (gen != generation) return;
                buildingsGroup.getChildren().clear();
                for (OsmBuildings.Band band : meshes.bands()) {
                    PhongMaterial wallMat = new PhongMaterial(band.color());
                    wallMat.setSpecularColor(Color.gray(0.08));
                    PhongMaterial roofMat = new PhongMaterial(band.color().interpolate(Color.WHITE, 0.28));
                    roofMat.setSpecularColor(Color.gray(0.12));
                    for (TriangleMesh m : band.walls()) {
                        MeshView v = new MeshView(m); v.setMaterial(wallMat); v.setCullFace(CullFace.NONE);
                        buildingsGroup.getChildren().add(v);
                    }
                    for (TriangleMesh m : band.roofs()) {
                        MeshView v = new MeshView(m); v.setMaterial(roofMat); v.setCullFace(CullFace.NONE);
                        buildingsGroup.getChildren().add(v);
                    }
                }
                buildingCount = meshes.buildingCount();
                applyBuildingVisibility();
                updateStatus();
            });
        });
    }

    /** Buildings hide on satellite imagery (the photo already shows them), as on web map satellite views. */
    private void applyBuildingVisibility() {
        buildingsGroup.setVisible(buildingsOn && basemap != OsmDataSource.Basemap.SATELLITE);
    }

    /** Ground height (scene y, negative is up) under a scene point. */
    private double groundY(double x, double z) {
        return proj == null ? 0 : -terrain.height(proj.latAt(z), proj.lonAt(x));
    }

    // =====================================================================================================
    // Camera
    // =====================================================================================================

    private void requestCameraUpdate() {
        if (cameraDirty) return;
        cameraDirty = true;
        Platform.runLater(() -> {
            cameraDirty = false;
            updateCamera();
        });
    }

    private void updateCamera() {
        double p = Math.toRadians(pitch.get()), yw = Math.toRadians(yaw.get()), d = distance.get();
        double dx = Math.sin(yw) * Math.cos(p), dy = Math.sin(p), dz = Math.cos(yw) * Math.cos(p);
        double ty = groundY(targetX.get(), targetZ.get());
        camera.getTransforms().setAll(
                new Translate(targetX.get() - dx * d, ty - dy * d, targetZ.get() - dz * d),
                new Rotate(yaw.get(), Rotate.Y_AXIS),
                new Rotate(-pitch.get(), Rotate.X_AXIS));
        // Keep the far/near ratio modest (~1:1000) so the depth buffer stays precise and nothing z-fights.
        camera.setNearClip(Math.max(0.3, d * 0.04));
        camera.setFarClip(d * 30 + 1500);
        compassNeedle.setRotate(-yaw.get());
        updateScaleBar();

        // Map pins keep a roughly constant on-screen size; they sit just above each crown.
        double pinS = Math.max(0.05, d / 62.0);
        for (TreeNode n : nodes.values()) {
            n.pinScale.setX(pinS); n.pinScale.setY(pinS); n.pinScale.setZ(pinS);
            n.pinLift.setY(-n.topY() - pinS * 0.2);
            double ps = n.tree.id == selectedId ? 1.35 : 1.0;
            n.pin.setScaleX(ps); n.pin.setScaleY(ps); n.pin.setScaleZ(ps);
        }
        positionSelectionVisuals();
        updateOverlayLabels();
        lodDebounce.setOnFinished(e -> updateLodTiles());
        lodDebounce.playFromStart();
    }

    /**
     * Sharper OSM imagery near the camera: when zoomed in, z18/z19 tiles around the view centre are layered
     * over the base tiles (loaded on demand, cached, and evicted when far away), like a slippy map's zoom levels.
     */
    private void updateLodTiles() {
        if (proj == null) return;
        double d = distance.get();
        int z = d < 170 ? 19 : d < 480 ? 18 : -1;
        if (z <= zDetail) {
            lodTiles.setVisible(false);
            return;
        }
        lodTiles.setVisible(true);
        double radius = Math.min(d * 1.7, 700);
        double lat = proj.latAt(targetZ.get()), lon = proj.lonAt(targetX.get());
        double dLat = radius / 111320.0, dLon = radius / (111320.0 * Math.cos(Math.toRadians(lat)));
        int x0 = (int) Math.floor(GeoProjection.tileXf(lon - dLon, z)), x1 = (int) Math.floor(GeoProjection.tileXf(lon + dLon, z));
        int y0 = (int) Math.floor(GeoProjection.tileYf(lat + dLat, z)), y1 = (int) Math.floor(GeoProjection.tileYf(lat - dLat, z));
        double cx = GeoProjection.tileXf(lon, z), cy = GeoProjection.tileYf(lat, z);
        List<int[]> wanted = new ArrayList<>();
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) wanted.add(new int[]{x, y});
        wanted.sort(Comparator.comparingDouble(p -> Math.hypot(p[0] + 0.5 - cx, p[1] + 0.5 - cy)));
        if (wanted.size() > 48) wanted = wanted.subList(0, 48);
        int gen = generation;
        OsmDataSource.Basemap style = basemap;
        double depth = z == 19 ? -0.6 : -0.3;
        for (MeshView v : lodCache.values()) {
            String[] k = ((String) v.getProperties().get("lod")).split("/");
            v.setVisible(Integer.parseInt(k[0]) == z && Boolean.TRUE.equals(v.getProperties().get("ready")));
        }
        for (int[] p : wanted) {
            String key = z + "/" + p[0] + "/" + p[1];
            if (lodCache.containsKey(key)) continue;
            int tx = p[0], ty = p[1];
            double west = GeoProjection.tileLon(tx, z), east = GeoProjection.tileLon(tx + 1, z);
            double north = GeoProjection.tileLat(ty, z), south = GeoProjection.tileLat(ty + 1, z);
            MeshView tile = terrainPatch(west, south, east, north, depth, z == 19 ? 4 : 8, placeholderMaterial(true));
            tile.getProperties().put("lod", key);
            tile.setVisible(false); // stays hidden until its imagery arrives, so the coarser tile shows meanwhile
            lodCache.put(key, tile);
            lodTiles.getChildren().add(tile);
            tilePool.execute(() -> {
                byte[] png = OsmDataSource.tile(style, z, tx, ty, offline);
                Image img = png == null ? null : new Image(new ByteArrayInputStream(png));
                Platform.runLater(() -> {
                    if (gen != generation || img == null || img.isError()) return;
                    tile.setMaterial(tileMaterial(img));
                    tile.getProperties().put("ready", true);
                    int curZ = distance.get() < 170 ? 19 : distance.get() < 480 ? 18 : -1;
                    tile.setVisible(curZ == z);
                });
            });
        }
        // Evict the farthest cached tiles beyond a budget.
        if (lodCache.size() > 220) {
            List<String> keys = new ArrayList<>(lodCache.keySet());
            keys.sort(Comparator.comparingDouble(k -> -lodDistance(k, lat, lon)));
            for (String k : keys.subList(0, lodCache.size() - 160)) {
                MeshView v = lodCache.remove(k);
                lodTiles.getChildren().remove(v);
            }
        }
    }

    private static double lodDistance(String key, double lat, double lon) {
        String[] k = key.split("/");
        int z = Integer.parseInt(k[0]), x = Integer.parseInt(k[1]), y = Integer.parseInt(k[2]);
        return Math.hypot(GeoProjection.tileXf(lon, z) - x - 0.5, GeoProjection.tileYf(lat, z) - y - 0.5) / (1 << z);
    }

    private void updateOverlayLabels() {
        if (getScene() == null || getScene().getWindow() == null) return;
        double d = distance.get();
        for (Map.Entry<Integer, Label> e : tags.entrySet()) {
            TreeNode n = nodes.get(e.getKey());
            Label tag = e.getValue();
            boolean want = n != null && showTags && n.root.isVisible() && (d < 2500 || e.getKey() == selectedId);
            if (!want) { tag.setVisible(false); continue; }
            Point2D p = project(n.x, n.baseY - n.topY() - Math.max(0.05, d / 62.0) * 2.6, n.z);
            if (p == null) { tag.setVisible(false); continue; }
            tag.autosize();
            tag.relocate(p.getX() - tag.getWidth() / 2, p.getY() - tag.getHeight() - 4);
            tag.setVisible(true);
            tag.setOpacity(e.getKey() == selectedId ? 1.0 : 0.88);
        }
    }

    /** Projects a world point to overlay coordinates, or null when it is behind the camera / off screen. */
    private Point2D project(double x, double y, double z) {
        Point3D cam = camera.getLocalToSceneTransform().transform(0, 0, 0);
        double p = Math.toRadians(pitch.get()), yw = Math.toRadians(yaw.get());
        double fx = Math.sin(yw) * Math.cos(p), fy = Math.sin(p), fz = Math.cos(yw) * Math.cos(p);
        if ((x - cam.getX()) * fx + (y - cam.getY()) * fy + (z - cam.getZ()) * fz <= camera.getNearClip()) return null;
        Point2D screen = world.localToScreen(x, y, z);
        if (screen == null) return null;
        Point2D local = labelLayer.screenToLocal(screen);
        if (local == null || local.getX() < -50 || local.getY() < -50
                || local.getX() > mapArea.getWidth() + 50 || local.getY() > mapArea.getHeight() + 50) return null;
        return local;
    }

    private void flyTo(double x, double z, double dist, Double newPitch, Double newYaw, double millis) {
        if (flight != null) flight.stop();
        double cy = yaw.get();
        double ty = newYaw == null ? cy : cy + ((((newYaw - cy) % 360) + 540) % 360 - 180);
        Interpolator ease = Interpolator.SPLINE(0.4, 0.0, 0.2, 1.0);
        flight = new Timeline(new KeyFrame(Duration.millis(millis),
                new KeyValue(targetX, x, ease), new KeyValue(targetZ, z, ease),
                new KeyValue(distance, clampDist(dist), ease),
                new KeyValue(pitch, newPitch == null ? pitch.get() : newPitch, ease),
                new KeyValue(yaw, ty, ease)));
        flight.play();
    }

    private static double clampDist(double d) {
        return Math.max(MIN_DIST, Math.min(MAX_DIST, d));
    }

    private void fitAll(boolean animate) {
        if (proj == null) return;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        int shown = 0;
        for (TreeNode n : nodes.values()) {
            if (!n.root.isVisible()) continue;
            minX = Math.min(minX, n.x); maxX = Math.max(maxX, n.x);
            minZ = Math.min(minZ, n.z); maxZ = Math.max(maxZ, n.z);
            shown++;
        }
        double cx = 0, cz = 0, span = 250;
        if (shown > 0) {
            cx = (minX + maxX) / 2; cz = (minZ + maxZ) / 2;
            span = Math.max(Math.max(maxX - minX, maxZ - minZ), 120);
        }
        double dist = span * 1.25 / Math.tan(Math.toRadians(camera.getFieldOfView() / 2)) / 2 + 80;
        if (animate) flyTo(cx, cz, dist, 55.0, HOME_YAW, 1100);
        else {
            targetX.set(cx); targetZ.set(cz); distance.set(clampDist(dist)); pitch.set(55); yaw.set(HOME_YAW);
        }
    }

    private void installNavigation() {
        final double[] last = new double[2];
        final boolean[] dragged = {false};
        subScene.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            last[0] = e.getSceneX(); last[1] = e.getSceneY();
            dragged[0] = false;
            subScene.requestFocus();
            if (flight != null) flight.stop();
        });
        subScene.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            double dx = e.getSceneX() - last[0], dy = e.getSceneY() - last[1];
            last[0] = e.getSceneX(); last[1] = e.getSceneY();
            if (Math.abs(dx) + Math.abs(dy) > 0.5) dragged[0] = true;
            boolean rotate = e.getButton() == MouseButton.SECONDARY || e.getButton() == MouseButton.MIDDLE
                    || e.isControlDown() || e.isShiftDown() || e.isMetaDown();
            if (rotate) {
                yaw.set(yaw.get() + dx * 0.35);
                pitch.set(Math.max(12, Math.min(89, pitch.get() + dy * 0.25)));
            } else {
                pan(dx, dy);
            }
            subScene.setCursor(rotate ? Cursor.MOVE : Cursor.CLOSED_HAND);
        });
        subScene.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> subScene.setCursor(hoverId >= 0 ? Cursor.HAND : Cursor.DEFAULT));
        subScene.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (dragged[0] || e.getButton() != MouseButton.PRIMARY) return;
            int id = pickTree(e.getPickResult());
            if (id >= 0) {
                select(id, e.getClickCount() >= 2);
            } else if (e.getClickCount() >= 2) {
                Point3D hit = e.getPickResult().getIntersectedPoint();
                Node node = e.getPickResult().getIntersectedNode();
                if (hit != null && node != null) {
                    Point3D w = world.sceneToLocal(node.localToScene(hit));
                    if (w != null) flyTo(w.getX(), w.getZ(), distance.get() * 0.55, null, null, 700);
                }
            }
        });
        subScene.addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            int id = pickTree(e.getPickResult());
            if (id != hoverId) {
                hoverId = id;
                subScene.setCursor(id >= 0 ? Cursor.HAND : Cursor.DEFAULT);
            }
            Tree t = id >= 0 ? trees.get(id) : null;
            if (t != null) {
                hoverTip.setText(safe(t.species, "Tree") + "  ·  #" + t.id + "\n" + safe(t.status, "Unknown status") + "  ·  " + fmt(t.height) + " m");
                hoverTip.autosize();
                Point2D p = labelLayer.sceneToLocal(e.getSceneX(), e.getSceneY());
                hoverTip.relocate(p.getX() + 16, p.getY() + 12);
                hoverTip.setVisible(true);
            } else {
                hoverTip.setVisible(false);
            }
        });
        subScene.addEventHandler(MouseEvent.MOUSE_EXITED, e -> hoverTip.setVisible(false));
        subScene.addEventHandler(ScrollEvent.SCROLL, e -> {
            if (e.getDeltaY() == 0) return;
            if (flight != null) flight.stop();
            double factor = Math.exp(-e.getDeltaY() * 0.0022);
            double before = distance.get();
            double after = clampDist(before * factor);
            // Zoom toward the point under the cursor.
            double mx = e.getX() - mapArea.getWidth() / 2, my = e.getY() - mapArea.getHeight() / 2;
            double k = (1 - after / before);
            pan(-mx * k, -my * k);
            distance.set(after);
        });
        subScene.setOnKeyPressed(e -> {
            double step = 60;
            KeyCode c = e.getCode();
            if (c == KeyCode.LEFT || c == KeyCode.A) pan(step, 0);
            else if (c == KeyCode.RIGHT || c == KeyCode.D) pan(-step, 0);
            else if (c == KeyCode.UP || c == KeyCode.W) pan(0, step);
            else if (c == KeyCode.DOWN || c == KeyCode.S) pan(0, -step);
            else if (c == KeyCode.Q) yaw.set(yaw.get() - 10);
            else if (c == KeyCode.E) yaw.set(yaw.get() + 10);
            else if (c == KeyCode.PLUS || c == KeyCode.EQUALS || c == KeyCode.ADD) zoomBy(0.75);
            else if (c == KeyCode.MINUS || c == KeyCode.SUBTRACT) zoomBy(1.33);
            else if (c == KeyCode.ESCAPE) clearSelection();
            else return;
            e.consume();
        });
    }

    /** Pans the camera target by a screen-space delta (pixels). */
    private void pan(double dxPx, double dyPx) {
        double h = Math.max(1, mapArea.getHeight());
        double mpp = 2 * distance.get() * Math.tan(Math.toRadians(camera.getFieldOfView() / 2)) / h;
        double yw = Math.toRadians(yaw.get());
        double rx = Math.cos(yw), rz = -Math.sin(yw);  // screen right on the ground
        double fx = Math.sin(yw), fz = Math.cos(yw);   // screen up (forward) on the ground
        double tilt = 1 / Math.max(0.35, Math.sin(Math.toRadians(pitch.get())));
        targetX.set(targetX.get() - rx * dxPx * mpp + fx * dyPx * mpp * tilt);
        targetZ.set(targetZ.get() - rz * dxPx * mpp + fz * dyPx * mpp * tilt);
    }

    private void zoomBy(double factor) {
        flyTo(targetX.get(), targetZ.get(), distance.get() * factor, null, null, 260);
    }

    private int pickTree(PickResult r) {
        Node n = r == null ? null : r.getIntersectedNode();
        while (n != null && n != world) {
            if (n.getUserData() instanceof Integer id && nodes.containsKey(id)) return id;
            n = n.getParent();
        }
        return -1;
    }

    // =====================================================================================================
    // Selection
    // =====================================================================================================

    private void buildSelectionVisuals() {
        MeshView inner = Tree3DModel.ring(1.25, 1.42, ACCENT, 0.12);
        MeshView outer = Tree3DModel.ring(1.62, 1.68, Color.web("#8FD4AF"), 0.12);
        halo.getChildren().addAll(inner, outer);
        halo.setMouseTransparent(true);
        halo.setDepthTest(javafx.scene.DepthTest.DISABLE);
        haloPulse = new ScaleTransition(Duration.millis(1100), halo);
        haloPulse.setFromX(0.92); haloPulse.setFromZ(0.92);
        haloPulse.setToX(1.12); haloPulse.setToZ(1.12);
        haloPulse.setAutoReverse(true);
        haloPulse.setCycleCount(Animation.INDEFINITE);
        beam = new Cylinder(1, 1, 12);
        PhongMaterial beamMat = new PhongMaterial(Color.web("#2FA36F"));
        beamMat.setSpecularColor(Color.WHITE);
        beam.setMaterial(beamMat);
        beam.setMouseTransparent(true);
        selectionGroup.getChildren().addAll(halo, beam);
        selectionGroup.setVisible(false);
    }

    private void positionSelectionVisuals() {
        TreeNode n = nodes.get(selectedId);
        if (n == null) { selectionGroup.setVisible(false); return; }
        selectionGroup.setVisible(true);
        double r = Math.max(n.crown * treeScale, distance.get() / 70.0);
        halo.setTranslateX(n.x); halo.setTranslateY(n.baseY); halo.setTranslateZ(n.z);
        halo.setScaleY(1);
        halo.getTransforms().setAll(new Scale(r, 1, r));
        double pinS = Math.max(0.05, distance.get() / 95.0);
        double top = n.topY() + pinS * 0.2;
        beam.setRadius(Math.max(0.03, pinS * 0.06));
        beam.setHeight(Math.max(0.1, top - n.topY() * 0.2));
        beam.setTranslateX(n.x); beam.setTranslateZ(n.z);
        beam.setTranslateY(n.baseY - (n.topY() * 0.2) - beam.getHeight() / 2);
        beam.setVisible(distance.get() > n.topY() * 6);
    }

    private void select(int id, boolean fly) {
        TreeNode n = nodes.get(id);
        Tree t = trees.get(id);
        if (n == null || t == null) return;
        selectedId = id;
        positionSelectionVisuals();
        haloPulse.playFromStart();
        showInfo(t, true);
        syncListSelection();
        if (fly) {
            double crownView = Math.max(n.topY() * 4.2, 45);
            double d = Math.min(distance.get(), crownView);
            flyTo(n.x, n.z, d, Math.min(pitch.get(), 40.0), null, 1000);
        } else if (!isOnScreen(n)) {
            flyTo(n.x, n.z, distance.get(), null, null, 700);
        }
        requestCameraUpdate();
    }

    private boolean isOnScreen(TreeNode n) {
        Point2D p = project(n.x, n.baseY - n.topY() / 2, n.z);
        return p != null && p.getX() > 290 && p.getX() < mapArea.getWidth() - 420 && p.getY() > 40 && p.getY() < mapArea.getHeight() - 60;
    }

    private void clearSelection() {
        selectedId = -1;
        selectionGroup.setVisible(false);
        haloPulse.stop();
        infoScroll.setVisible(false);
        syncingList = true;
        treeList.getSelectionModel().clearSelection();
        syncingList = false;
        requestCameraUpdate();
    }

    // =====================================================================================================
    // Overlay chrome
    // =====================================================================================================

    /** Floating map overlays: tree list (left), tree details (right), navigation, legend and notices. */
    private StackPane buildChrome() {
        StackPane chrome = new StackPane();
        chrome.setPickOnBounds(false);

        VBox left = buildTreePanel();
        // Leave room for the legend (and the offline notice above it when shown).
        left.maxHeightProperty().bind(javafx.beans.binding.Bindings.when(offlineNotice.visibleProperty())
                .then(mapArea.heightProperty().subtract(196)).otherwise(mapArea.heightProperty().subtract(132)));
        left.setMaxWidth(Region.USE_PREF_SIZE);
        StackPane.setAlignment(left, Pos.TOP_LEFT);
        StackPane.setMargin(left, new Insets(14, 0, 0, 14));

        infoPanel.setPadding(new Insets(18));
        infoPanel.setPrefWidth(326);
        infoScroll.getStyleClass().add("tm-panel");
        infoScroll.setFitToWidth(true);
        infoScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        infoScroll.setPrefWidth(344);
        infoScroll.setMaxWidth(344);
        infoScroll.maxHeightProperty().bind(mapArea.heightProperty().subtract(28));
        infoScroll.setVisible(false);
        // Wrapped so the panel hugs its content instead of stretching to the full map height.
        VBox infoWrap = new VBox(infoScroll);
        infoWrap.setAlignment(Pos.TOP_RIGHT);
        infoWrap.setPickOnBounds(false);
        infoWrap.setMaxWidth(Region.USE_PREF_SIZE);
        StackPane.setAlignment(infoWrap, Pos.TOP_RIGHT);
        StackPane.setMargin(infoWrap, new Insets(14, 66, 0, 0)); // the navigation column stays clear

        VBox nav = buildNavControls();
        nav.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(nav, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(nav, new Insets(0, 14, 34, 0));

        HBox attribBox = new HBox(attribution);
        attribBox.getStyleClass().add("tm-attrib");
        attribBox.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(attribBox, Pos.BOTTOM_RIGHT);

        HBox legend = buildLegend();
        emptyNotice.setText("No trees with map coordinates yet. Register a sapling and place its pin to see it here.");
        emptyNotice.getStyleClass().add("tm-notice");
        emptyNotice.setVisible(false);
        emptyNotice.setWrapText(true);
        emptyNotice.setMaxWidth(380);
        offlineNotice.setText("Offline: showing cached map tiles. Tiles cover Baguio and areas you viewed online.");
        offlineNotice.getStyleClass().add("tm-notice");
        offlineNotice.setWrapText(true);
        offlineNotice.setMaxWidth(380);
        offlineNotice.setVisible(offline);
        offlineNotice.managedProperty().bind(offlineNotice.visibleProperty());
        emptyNotice.managedProperty().bind(emptyNotice.visibleProperty());
        VBox notices = new VBox(8, emptyNotice, offlineNotice);
        notices.setAlignment(Pos.BOTTOM_LEFT);
        notices.setPickOnBounds(false);
        scaleBar.getStyleClass().add("tm-scale-bar");
        scaleBar.setMinHeight(6); scaleBar.setPrefHeight(6); scaleBar.setMaxHeight(6);
        scaleBar.setMaxWidth(Region.USE_PREF_SIZE);
        scaleText.getStyleClass().add("tm-scale-text");
        VBox scale = new VBox(2, scaleText, scaleBar);
        scale.getStyleClass().add("tm-scale");
        scale.setMaxWidth(Region.USE_PREF_SIZE);
        VBox bottomLeft = new VBox(8, notices, scale, legend);
        bottomLeft.setAlignment(Pos.BOTTOM_LEFT);
        bottomLeft.setPickOnBounds(false);
        bottomLeft.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(bottomLeft, Pos.BOTTOM_LEFT);
        StackPane.setMargin(bottomLeft, new Insets(0, 0, 14, 14));

        chrome.getChildren().addAll(left, bottomLeft, nav, attribBox, infoWrap);
        return chrome;
    }

    private HBox buildTopBar() {
        Label title = new Label("3D Forest Map");
        title.getStyleClass().add("tm-heading");
        Label sub = new Label("Every registered tree, standing at its recorded location in Baguio City");
        sub.getStyleClass().add("tm-sub");
        sub.setMinWidth(0);
        VBox titles = new VBox(2, title, sub);
        titles.setMinWidth(180);

        statusLabel.getStyleClass().add("tm-status");
        statusLabel.setMinWidth(0); // the status line is the first thing to give way on narrow windows

        javafx.scene.control.MenuButton layers = buildLayersMenu();

        ToggleButton onlineBtn = new ToggleButton("Online");
        ToggleButton offlineBtn = new ToggleButton("Offline");
        javafx.scene.control.ToggleGroup tiles = new javafx.scene.control.ToggleGroup();
        onlineBtn.setToggleGroup(tiles);
        offlineBtn.setToggleGroup(tiles);
        onlineBtn.getStyleClass().addAll("tm-segment", "tm-segment-left");
        offlineBtn.getStyleClass().addAll("tm-segment", "tm-segment-right");
        (offline ? offlineBtn : onlineBtn).setSelected(true);
        offlineBtn.setId("btnTilesOffline");
        onlineBtn.setTooltip(new Tooltip("Download OpenStreetMap tiles as needed (cached for later offline use)"));
        offlineBtn.setTooltip(new Tooltip("Use only cached and bundled map tiles: for the field without internet"));
        tiles.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null) { was.setSelected(true); return; } // keep exactly one selected
            boolean off = now == offlineBtn;
            if (off == offline) return;
            offline = off;
            ClientConfig.saveMapOfflineTiles(offline);
            offlineNotice.setVisible(offline);
            rebuildArea();
            requestCameraUpdate();
        });
        HBox segment = new HBox(0, onlineBtn, offlineBtn);
        segment.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(12, titles, spacer, statusLabel, layers, segment);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("tm-topbar");
        for (Node n : List.of(layers, onlineBtn, offlineBtn)) ((Region) n).setMinWidth(Region.USE_PREF_SIZE);
        bar.widthProperty().addListener((o, old, w) -> {
            statusLabel.setVisible(w.doubleValue() > 1080);
            statusLabel.setManaged(w.doubleValue() > 1080);
            sub.setVisible(w.doubleValue() > 760);
        });
        return bar;
    }

    /** Map layers dropdown: basemap style, 3D buildings, terrain, labels and tree size. */
    private javafx.scene.control.MenuButton buildLayersMenu() {
        javafx.scene.control.MenuButton menu = new javafx.scene.control.MenuButton("Layers");
        menu.getStyleClass().add("tm-menu");
        menu.setGraphic(RegistryStyle.navIcon("Layers"));
        javafx.scene.control.ToggleGroup styles = new javafx.scene.control.ToggleGroup();
        javafx.scene.control.MenuItem header = new javafx.scene.control.MenuItem("BASEMAP");
        header.setDisable(true);
        menu.getItems().add(header);
        for (OsmDataSource.Basemap b : OsmDataSource.Basemap.values()) {
            javafx.scene.control.RadioMenuItem item = new javafx.scene.control.RadioMenuItem(b.label);
            item.setToggleGroup(styles);
            item.setSelected(b == basemap);
            item.setOnAction(e -> {
                if (basemap == b) return;
                basemap = b;
                reloadImagery();
            });
            menu.getItems().add(item);
        }
        javafx.scene.control.CheckMenuItem buildingsItem = new javafx.scene.control.CheckMenuItem("3D buildings");
        buildingsItem.setSelected(buildingsOn);
        buildingsItem.setOnAction(e -> {
            buildingsOn = buildingsItem.isSelected();
            if (buildingsOn && buildingsGroup.getChildren().isEmpty()) loadBuildings(generation);
            applyBuildingVisibility();
            updateStatus();
        });
        javafx.scene.control.CheckMenuItem terrainItem = new javafx.scene.control.CheckMenuItem("Terrain (hills)");
        terrainItem.setSelected(terrainOn);
        terrainItem.setOnAction(e -> { terrainOn = terrainItem.isSelected(); rebuildArea(); });
        javafx.scene.control.CheckMenuItem labelsItem = new javafx.scene.control.CheckMenuItem("Tree labels");
        labelsItem.setSelected(showTags);
        labelsItem.setOnAction(e -> { showTags = labelsItem.isSelected(); requestCameraUpdate(); });

        Label scaleLabel = new Label("Tree size ×" + (int) treeScale);
        Slider scale = new Slider(1, 15, treeScale);
        scale.setPrefWidth(150);
        scale.valueProperty().addListener((o, x, v) -> {
            treeScale = Math.round(v.doubleValue());
            scaleLabel.setText(treeScale == 1 ? "Tree size: true scale" : "Tree size ×" + (int) treeScale);
            for (TreeNode n : nodes.values()) n.applyScale();
            requestCameraUpdate();
        });
        VBox sizeBox = new VBox(4, scaleLabel, scale);
        sizeBox.setPadding(new Insets(4, 6, 4, 6));
        javafx.scene.control.CustomMenuItem sizeItem = new javafx.scene.control.CustomMenuItem(sizeBox, false);
        menu.getItems().addAll(new javafx.scene.control.SeparatorMenuItem(), buildingsItem, terrainItem, labelsItem,
                new javafx.scene.control.SeparatorMenuItem(), sizeItem);
        menu.setTooltip(new Tooltip("Basemap style, 3D buildings, terrain, labels and tree size"));
        return menu;
    }

    /** Swaps ground imagery to the current basemap without moving the camera or touching terrain and trees. */
    private void reloadImagery() {
        generation++;
        int gen = generation;
        detailTiles.getChildren().clear();
        contextTiles.getChildren().clear();
        lodTiles.getChildren().clear();
        lodCache.clear();
        tilesLoaded.set(0);
        tilesTotal = 0;
        buildGround(gen, expand(area, 2.6));
        if (buildingsGroup.getChildren().isEmpty() && buildingsOn) loadBuildings(gen);
        applyBuildingVisibility();
        updateStatus();
        requestCameraUpdate();
    }

    private VBox buildTreePanel() {
        Label caption = new Label("TREES ON MAP");
        caption.getStyleClass().add("tm-caption");
        countLabel.getStyleClass().add("tm-sub");
        HBox head = new HBox(8, caption, new Region(), countLabel);
        HBox.setHgrow(head.getChildren().get(1), Priority.ALWAYS);
        search.setPromptText("Search species or ID");
        search.textProperty().addListener((o, a, b) -> refreshList());
        statusFilter.getItems().addAll("All statuses", "Healthy", "Monitoring", "At Risk", "Destroyed");
        statusFilter.setValue("All statuses");
        statusFilter.setMaxWidth(Double.MAX_VALUE);
        statusFilter.valueProperty().addListener((o, a, b) -> refreshList());

        treeList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Tree t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setGraphic(null); setText(null); return; }
                Circle dot = new Circle(5, Tree3DModel.healthColor(Tree3DModel.health(t.status)));
                Label name = new Label(safe(t.species, "Unnamed tree"));
                name.setStyle("-fx-font-weight: bold; -fx-text-fill: #18231e;");
                Label meta = new Label("#" + t.id + "  ·  " + safe(t.status, "Unknown") + "  ·  " + fmt(t.height) + " m");
                meta.getStyleClass().add("tm-sub");
                VBox text = new VBox(1, name, meta);
                HBox row = new HBox(9, dot, text);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
                setText(null);
            }
        });
        treeList.setPlaceholder(new Label("No trees match."));
        treeList.getSelectionModel().selectedItemProperty().addListener((o, a, t) -> {
            if (!syncingList && t != null) select(t.id, true);
        });
        VBox.setVgrow(treeList, Priority.ALWAYS);

        Button fit = new Button("Fit all trees");
        fit.getStyleClass().add("tm-btn");
        fit.setMaxWidth(Double.MAX_VALUE);
        fit.setOnAction(e -> fitAll(true));

        VBox panel = new VBox(9, head, search, statusFilter, treeList, fit);
        panel.getStyleClass().add("tm-panel");
        panel.setPadding(new Insets(14));
        panel.setPrefWidth(258);
        panel.setMaxHeight(Double.MAX_VALUE);
        return panel;
    }

    private HBox buildLegend() {
        HBox legend = new HBox(12);
        legend.getStyleClass().add("tm-panel");
        legend.setPadding(new Insets(8, 12, 8, 12));
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Label cap = new Label("HEALTH");
        cap.getStyleClass().add("tm-caption");
        legend.getChildren().add(cap);
        for (Tree3DModel.Health h : new Tree3DModel.Health[]{Tree3DModel.Health.HEALTHY, Tree3DModel.Health.MONITORING,
                Tree3DModel.Health.AT_RISK, Tree3DModel.Health.DESTROYED}) {
            Label l = new Label(healthName(h), new Circle(5, Tree3DModel.healthColor(h)));
            l.setGraphicTextGap(6);
            legend.getChildren().add(l);
        }
        return legend;
    }

    private VBox buildNavControls() {
        Button zoomIn = navButton("+", "Zoom in", () -> zoomBy(0.6));
        Button zoomOut = navButton("−", "Zoom out", () -> zoomBy(1.65));
        Button rotL = navButton("⟲", "Rotate left", () -> flyTo(targetX.get(), targetZ.get(), distance.get(), null, yaw.get() - 45, 450));
        Button rotR = navButton("⟳", "Rotate right", () -> flyTo(targetX.get(), targetZ.get(), distance.get(), null, yaw.get() + 45, 450));
        Button tilt = navButton("3D", "Toggle top-down / perspective view", () -> {
            boolean flat = pitch.get() > 80;
            flyTo(targetX.get(), targetZ.get(), distance.get(), flat ? 50.0 : 89.0, null, 600);
        });
        tilt.setStyle("-fx-font-size: 11px;");
        Button fit = navButton("⤢", "Fit all trees", () -> fitAll(true));

        compassNeedle.setContent("M0 -13 L5 0 L0 -3 L-5 0 Z M0 13 L5 0 L0 3 L-5 0 Z");
        compassNeedle.setStyle("-fx-fill: #20865B;");
        Label n = new Label("N");
        n.setStyle("-fx-text-fill: #9D2235; -fx-font-size: 9px; -fx-font-weight: bold;");
        n.setTranslateY(-19);
        StackPane compass = new StackPane(compassRing(), compassNeedle, n);
        compass.setCursor(Cursor.HAND);
        Tooltip.install(compass, new Tooltip("Reset to north"));
        compass.setOnMouseClicked(e -> flyTo(targetX.get(), targetZ.get(), distance.get(), null, 0.0, 600));
        compass.setPadding(new Insets(6, 0, 4, 0));

        VBox box = new VBox(6, compass, zoomIn, zoomOut, rotL, rotR, tilt, fit);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(0, 12, 0, 0));
        box.setPickOnBounds(false);
        return box;
    }

    private static Circle compassRing() {
        Circle c = new Circle(19, Color.WHITE);
        c.setStroke(Color.web("#DCE5DF"));
        c.setEffect(new javafx.scene.effect.DropShadow(8, 0, 2, Color.rgb(28, 58, 42, 0.16)));
        return c;
    }

    private Button navButton(String text, String tip, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("tm-nav");
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(e -> action.run());
        b.setFocusTraversable(false);
        return b;
    }

    private void refreshList() {
        List<Tree> filtered = new ArrayList<>();
        for (Tree t : trees.values()) if (filterMatches(t)) filtered.add(t);
        filtered.sort(Comparator.comparing((Tree t) -> safe(t.species, "").toLowerCase(Locale.ROOT)).thenComparingInt(t -> t.id));
        syncingList = true;
        listItems.setAll(filtered);
        syncingList = false;
        countLabel.setText(filtered.size() + " / " + trees.size());
        for (TreeNode n : nodes.values()) applyFilterVisibility(n);
        syncListSelection();
        requestCameraUpdate();
    }

    private boolean filterMatches(Tree t) {
        String status = statusFilter.getValue();
        if (status != null && !"All statuses".equals(status)
                && !Objects.equals(healthName(Tree3DModel.health(t.status)), status)) return false;
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT).replaceFirst("^#", "");
        if (q.isEmpty()) return true;
        return safe(t.species, "").toLowerCase(Locale.ROOT).contains(q) || String.valueOf(t.id).contains(q);
    }

    private void applyFilterVisibility(TreeNode n) {
        boolean v = filterMatches(n.tree) || n.tree.id == selectedId;
        n.root.setVisible(v);
        n.pin.setVisible(v);
    }

    private void syncListSelection() {
        syncingList = true;
        Tree sel = trees.get(selectedId);
        if (sel != null && listItems.contains(sel)) {
            treeList.getSelectionModel().select(sel);
            treeList.scrollTo(sel);
        } else {
            treeList.getSelectionModel().clearSelection();
        }
        syncingList = false;
    }

    private void updateStatus() {
        StringBuilder sb = new StringBuilder();
        int loaded = tilesLoaded.get();
        if (terrainLoading) sb.append("Loading terrain…");
        else sb.append(loaded < tilesTotal ? "Loading map " + loaded + "/" + tilesTotal : "Map ready");
        if (buildingsOn && buildingCount > 0) sb.append("  ·  ").append(String.format(Locale.US, "%,d", buildingCount)).append(" buildings");
        sb.append("  ·  ").append(trees.size()).append(trees.size() == 1 ? " tree" : " trees");
        statusLabel.setText(sb.toString());
        attribution.setText(basemap.attribution + (terrainOn && !terrain.isFlat() ? " · Terrain: Mapzen/AWS" : "")
                + (buildingsOn && buildingCount > 0 ? " · Buildings: OpenStreetMap" : ""));
    }

    /** Scale bar: a round distance that fits in about 110 px at the view centre. */
    private void updateScaleBar() {
        double h = Math.max(1, mapArea.getHeight());
        double mpp = 2 * distance.get() * Math.tan(Math.toRadians(camera.getFieldOfView() / 2)) / h;
        double target = mpp * 110, nice = 1;
        double pow = Math.pow(10, Math.floor(Math.log10(target)));
        for (double m : new double[]{1, 2, 5, 10}) if (m * pow <= target) nice = m * pow;
        scaleBar.setPrefWidth(Math.max(20, nice / mpp));
        scaleText.setText(nice >= 1000 ? String.format(Locale.US, "%.0f km", nice / 1000) : String.format(Locale.US, "%.0f m", nice));
    }

    // =====================================================================================================
    // Tree information panel
    // =====================================================================================================

    private int historyRequest;

    private void showInfo(Tree t, boolean reloadHistory) {
        if (t == null) return;
        infoScroll.setVisible(true);
        infoPanel.getChildren().clear();
        Tree3DModel.Health h = Tree3DModel.health(t.status);

        Label species = new Label(safe(t.species, "Unnamed tree"));
        species.getStyleClass().add("tm-title");
        species.setWrapText(true);
        Button close = new Button("✕");
        close.getStyleClass().add("tm-btn");
        close.setOnAction(e -> clearSelection());
        HBox titleRow = new HBox(8, species, new Region(), close);
        HBox.setHgrow(titleRow.getChildren().get(1), Priority.ALWAYS);
        titleRow.setAlignment(Pos.TOP_LEFT);

        Label idChip = new Label("ID #" + t.id);
        idChip.getStyleClass().add("tm-id-chip");
        Label healthChip = new Label(safe(t.status, "Unknown"));
        healthChip.getStyleClass().add("tm-chip");
        healthChip.setStyle("-fx-background-color: " + web(Tree3DModel.healthColor(h)) + ";");
        Label form = new Label(switch (Tree3DModel.form(t.species)) {
            case CONIFER -> "Conifer";
            case PALM -> "Palm";
            default -> "Broadleaf";
        });
        form.getStyleClass().add("tm-id-chip");
        HBox chips = new HBox(6, idChip, healthChip, form);

        double[] where = GeoCoordFormat.tryParse(t.location);
        Label place = new Label(where == null ? "Location not recorded"
                : String.format(Locale.US, "%.5f° N, %.5f° E · Baguio City", where[0], where[1]));
        place.setGraphic(RegistryStyle.navIcon("Pin"));
        place.getStyleClass().add("tm-sub");
        // Highlight card: the health status is the most important fact, so it leads.
        Label healthBig = new Label(safe(t.status, "Unknown status"));
        healthBig.getStyleClass().add("tm-health-value");
        healthBig.setStyle("-fx-text-fill: " + web(Tree3DModel.healthColor(h)) + ";");
        Label healthSub = new Label(ageText(t.plantedDate) + "  ·  " + fmt(t.height) + " m tall");
        healthSub.getStyleClass().add("tm-sub");
        Label healthCap = new Label("HEALTH STATUS");
        healthCap.getStyleClass().add("tm-caption");
        VBox healthCard = new VBox(2, healthCap, healthBig, healthSub);
        healthCard.getStyleClass().add("tm-health-card");
        healthCard.setStyle("-fx-border-color: transparent transparent transparent " + web(Tree3DModel.healthColor(h)) + ";");
        infoPanel.getChildren().addAll(place, titleRow, healthCard, chips);

        if (t.imageBytes != null && t.imageBytes.length > 0) {
            Image img = new Image(new ByteArrayInputStream(t.imageBytes));
            if (!img.isError()) {
                ImageView iv = new ImageView(img);
                iv.setPreserveRatio(true);
                iv.setFitWidth(294);
                iv.setFitHeight(170);
                iv.setSmooth(true);
                StackPane frame = new StackPane(iv);
                frame.setStyle("-fx-background-color: #eef3ef; -fx-background-radius: 10;");
                infoPanel.getChildren().add(frame);
            }
        }

        double[] ll = GeoCoordFormat.tryParse(t.location);
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(7);
        int r = 0;
        addRow(grid, r++, "Tree ID", "#" + t.id);
        addRow(grid, r++, "Species", safe(t.species, "—"));
        addRow(grid, r++, "Height", fmt(t.height) + " m");
        addRow(grid, r++, "Trunk diameter", fmt(t.diameter) + " cm");
        addRow(grid, r++, "Age", ageText(t.plantedDate));
        addRow(grid, r++, "Planted", safe(t.plantedDate, "Unknown"));
        addRow(grid, r++, "Coordinates", ll == null ? "—" : String.format(Locale.US, "%.6f° N, %.6f° E", ll[0], ll[1]));
        addRow(grid, r++, "Recorded by", safe(t.ownerUsername, "—"));
        infoPanel.getChildren().add(grid);

        if (t.notes != null && !t.notes.isBlank()) {
            Label notesCap = new Label("FIELD NOTES");
            notesCap.getStyleClass().add("tm-caption");
            Label notes = new Label(t.notes.trim());
            notes.setWrapText(true);
            notes.getStyleClass().add("tm-value");
            infoPanel.getChildren().addAll(notesCap, notes);
        }

        Button center = new Button("Center view");
        center.getStyleClass().add("tm-btn");
        center.setOnAction(e -> select(t.id, true));
        Button open = new Button("Open record");
        open.getStyleClass().addAll("tm-btn", "tm-primary");
        open.setOnAction(e -> host.openRecord(trees.getOrDefault(t.id, t)));
        Button registry = new Button("Show in registry");
        registry.getStyleClass().add("tm-btn");
        registry.setOnAction(e -> host.showInRegistry(trees.getOrDefault(t.id, t)));
        HBox actions = new HBox(6, open, center);
        HBox actions2 = new HBox(6, registry);
        if (host.canEdit(t)) {
            Button edit = new Button("Edit tree");
            edit.getStyleClass().add("tm-btn");
            edit.setOnAction(e -> host.editRecord(trees.getOrDefault(t.id, t)));
            actions2.getChildren().add(edit);
        }
        infoPanel.getChildren().addAll(new Separator(), actions, actions2);

        Label histCap = new Label("MONITORING HISTORY");
        histCap.getStyleClass().add("tm-caption");
        VBox history = new VBox(2);
        history.setId("history");
        infoPanel.getChildren().addAll(new Separator(), histCap, history);
        if (reloadHistory || cachedHistoryFor != t.id) {
            loadHistoryInto(history, t.id);
        } else {
            fillHistory(history, cachedHistory);
        }
    }

    private int cachedHistoryFor = -1;
    private List<String> cachedHistory = List.of();

    private void loadHistoryInto(VBox box, int treeId) {
        int req = ++historyRequest;
        Label loading = new Label("Loading history…");
        loading.getStyleClass().add("tm-muted");
        box.getChildren().setAll(loading);
        dataPool.execute(() -> {
            List<String> lines;
            String error = null;
            try {
                lines = host.loadHistory(treeId);
            } catch (Exception ex) {
                lines = List.of();
                error = "History unavailable (" + ex.getClass().getSimpleName() + ").";
            }
            List<String> fl = lines == null ? List.of() : lines;
            String err = error;
            Platform.runLater(() -> {
                if (req != historyRequest) return;
                cachedHistoryFor = treeId;
                cachedHistory = fl;
                if (err != null) {
                    Label l = new Label(err);
                    l.getStyleClass().add("tm-muted");
                    box.getChildren().setAll(l);
                } else {
                    fillHistory(box, fl);
                }
            });
        });
    }

    private static void fillHistory(VBox box, List<String> lines) {
        box.getChildren().clear();
        if (lines.isEmpty()) {
            Label l = new Label("No recorded activity for this tree yet.");
            l.getStyleClass().add("tm-muted");
            box.getChildren().add(l);
            return;
        }
        for (String line : lines) {
            // Server format: "timestamp | user | event"
            String[] p = line.split("\\s\\|\\s", 3);
            VBox item = new VBox(1);
            item.getStyleClass().add("tm-history-item");
            if (p.length == 3) {
                Label what = new Label(p[2]);
                what.setWrapText(true);
                what.getStyleClass().add("tm-value");
                Label when = new Label(p[0] + "  ·  " + p[1]);
                when.getStyleClass().add("tm-sub");
                item.getChildren().addAll(what, when);
            } else {
                Label what = new Label(line);
                what.setWrapText(true);
                what.getStyleClass().add("tm-value");
                item.getChildren().add(what);
            }
            box.getChildren().add(item);
        }
    }

    private static void addRow(GridPane g, int row, String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add("tm-key");
        k.setMinWidth(Region.USE_PREF_SIZE);
        Label v = new Label(value);
        v.getStyleClass().add("tm-value");
        v.setWrapText(true);
        g.add(k, 0, row);
        g.add(v, 1, row);
    }

    static String ageText(String planted) {
        if (planted == null || planted.isBlank()) return "Unknown";
        try {
            LocalDate d = LocalDate.parse(planted.trim());
            Period p = Period.between(d, LocalDate.now());
            if (p.isNegative()) return "Not yet planted";
            if (p.getYears() == 0 && p.getMonths() == 0) return p.getDays() + (p.getDays() == 1 ? " day" : " days");
            StringBuilder sb = new StringBuilder();
            if (p.getYears() > 0) sb.append(p.getYears()).append(p.getYears() == 1 ? " yr" : " yrs");
            if (p.getMonths() > 0) sb.append(sb.length() > 0 ? " " : "").append(p.getMonths()).append(" mo");
            return sb + " since planting";
        } catch (DateTimeParseException e) {
            return "Unknown";
        }
    }

    // =====================================================================================================
    // helpers
    // =====================================================================================================

    private static String healthName(Tree3DModel.Health h) {
        return switch (h) {
            case HEALTHY -> "Healthy";
            case MONITORING -> "Monitoring";
            case AT_RISK -> "At Risk";
            case DESTROYED -> "Destroyed";
            default -> "Unknown";
        };
    }

    private static String safe(String s, String dflt) {
        return s == null || s.isBlank() ? dflt : s.trim();
    }

    private static String fmt(double v) {
        if (v == Math.rint(v)) return String.format(Locale.US, "%.0f", v);
        return String.format(Locale.US, "%.2f", v).replaceAll("0+$", "");
    }

    private static String web(Color c) {
        return String.format("#%02X%02X%02X", (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}
