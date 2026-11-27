import Server.*;
import client_GUI.*;
import JSONUtil.UserJSONUtil;
import Model.Tree;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.rmi.Remote;
import java.rmi.registry.Registry;
import java.util.*;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.BorderPane;
import javafx.util.Duration;

/**
 * Verifies the 3D Forest Map inside the real dashboard against a temporary copy of the demonstration data:
 * every tree becomes a 3D model at its coordinates, registry and map selections stay in sync, edits made through
 * the registry service reach the 3D view, and the monitoring history comes from the server log.
 * Screenshots go to docs/screens/3d-*.png. Uses offline mode, so the ground shows cached tiles only.
 */
public class Map3DScreens {
    static Path destination, isolated;
    static final Map<String, Remote> services = new HashMap<>();
    static final Registry directory = new Registry() {
        public Remote lookup(String name) { return services.get(name); }
        public String[] list() { return services.keySet().toArray(String[]::new); }
        public void bind(String n, Remote r) { throw new UnsupportedOperationException(); }
        public void rebind(String n, Remote r) { throw new UnsupportedOperationException(); }
        public void unbind(String n) { throw new UnsupportedOperationException(); }
    };
    static int passed;

    static void capture(String name, Scene scene) throws Exception {
        WritableImage image = scene.snapshot(null);
        int w = (int) image.getWidth(), h = (int) image.getHeight();
        int[] pixels = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        BufferedImage png = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        png.setRGB(0, 0, w, h, pixels, 0, w);
        ImageIO.write(png, "png", destination.resolve(name + ".png").toFile());
        System.out.println("  screenshot " + name + ".png");
    }
    static void navigate(Scene scene, String name) {
        scene.getRoot().applyCss();
        scene.getRoot().lookupAll(".nav").stream()
                .filter(n -> n instanceof Button b && name.equals(b.getText()))
                .map(n -> (Button) n).findFirst().orElseThrow().fire();
        scene.getRoot().applyCss();
        scene.getRoot().layout();
    }
    static void check(String id, boolean result) {
        if (!result) throw new AssertionError(id);
        passed++;
        System.out.println("PASS  " + id);
    }
    static void finish(int code) {
        try (var files = Files.walk(isolated)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (Exception ex) { ex.printStackTrace(); code = 1; }
        if (code == 0) System.out.println(passed + " checks passed.");
        System.exit(code);
    }
    static void later(double seconds, ThrowingRunnable step) {
        PauseTransition p = new PauseTransition(Duration.seconds(seconds));
        p.setOnFinished(e -> { try { step.run(); } catch (Throwable t) { t.printStackTrace(); finish(1); } });
        p.play();
    }
    interface ThrowingRunnable { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        destination = Files.createDirectories(Path.of("docs", "screens"));
        isolated = Files.createTempDirectory("lorax-3d-");
        Files.copy(Path.of("demo-data", "tree_data.json"), isolated.resolve("tree_data.json"));
        // Reuse any OSM data this machine already cached, so the screenshots show real map imagery.
        Path cache = Path.of("data", "cache", "osm");
        if (Files.isDirectory(cache)) {
            Path target = isolated.resolve("cache").resolve("osm");
            try (var walk = Files.walk(cache)) {
                for (Path p : walk.toList()) {
                    Path dest = target.resolve(cache.relativize(p).toString());
                    if (Files.isDirectory(p)) Files.createDirectories(dest); else Files.copy(p, dest);
                }
            }
        }
        System.setProperty("lorax.data.dir", isolated.toString());
        Path config = isolated.resolve("client.properties");
        Files.writeString(config, "map.offline.tiles=true");
        System.setProperty("lorax.client.config", config.toString());
        String pw = UUID.randomUUID().toString();
        UserJSONUtil.createLocalAdministrator(pw);
        UserService users = new UserServiceImpl();
        TreeService trees = new TreeServiceImpl();
        services.put("UserService", users); services.put("TreeService", trees);
        services.put("AdminService", new AdminServiceImpl()); services.put("ChatService", new ChatServiceImpl());
        users.register("ranger_ana", pw);
        String adminToken = users.authenticate("admin", pw).substring(9);
        String token = users.authenticate("ranger_ana", pw).substring(9);

        Platform.startup(() -> {
            try {
                Platform.setImplicitExit(false);
                TreeDashboard dashboard = new TreeDashboard(directory, "ranger_ana", token);
                dashboard.show();
                Scene scene = dashboard.getScene();
                later(1.5, () -> {
                    check("M01 registry offers the 3D map", scene.getRoot().lookupAll(".nav").stream()
                            .anyMatch(n -> n instanceof Button b && "3D Map".equals(b.getText())));
                    navigate(scene, "3D Map");
                    Node center = ((BorderPane) scene.getRoot()).getCenter();
                    check("M02 3D map is the dashboard workspace", center instanceof TreeMap3D);
                    TreeMap3D map = (TreeMap3D) center;
                    check("M03 every demonstration tree is a 3D model", map.getRenderedTreeCount() == 4);
                    boolean placed = true;
                    for (Tree t : trees.getAllTrees(token)) {
                        String[] ll = t.location.split(",");
                        double[] want = map.project(Double.parseDouble(ll[0]), Double.parseDouble(ll[1]));
                        double[] got = map.getScenePosition(t.id);
                        placed &= got != null && Math.abs(got[0] - want[0]) < 0.01 && Math.abs(got[1] - want[1]) < 0.01;
                    }
                    check("M04 trees sit at their recorded coordinates in the shared map projection", placed);
                    map.applyCss();
                    ListView<?> list = map.lookupAll(".list-view").stream()
                            .filter(n -> n instanceof ListView<?> l && !l.getItems().isEmpty() && l.getItems().get(0) instanceof Tree)
                            .map(n -> (ListView<?>) n).findFirst().orElse(null);
                    check("M05 map tree list mirrors the registry", list != null && list.getItems().size() == 4);
                    later(4, () -> {
                        capture("3d-map-overview", scene);
                        navigate(scene, "Registry");
                        // The registry card's "3D" button flies to that tree.
                        scene.getRoot().applyCss(); scene.getRoot().layout();
                        Button locate = scene.getRoot().lookupAll(".button").stream()
                                .filter(n -> n instanceof Button b && "View in 3D".equals(b.getText()))
                                .map(n -> (Button) n).findFirst().orElseThrow();
                        locate.fire();
                        later(2.5, () -> {
                            TreeMap3D m = (TreeMap3D) ((BorderPane) scene.getRoot()).getCenter();
                            check("M06 registry card selects the tree on the 3D map", m.getSelectedTreeId() > 0);
                            int id = m.getSelectedTreeId();
                            capture("3d-map-selected", scene);
                            // An administrator edit through the normal service reaches the 3D view.
                            Tree stored = trees.getAllTrees(adminToken).stream().filter(x -> x.id == id).findFirst().orElseThrow();
                            // Send a copy, as the RMI client does (direct calls would otherwise share the stored object).
                            Tree t = new Tree(stored.id, stored.species, stored.location, stored.height, stored.diameter,
                                    stored.plantedDate, stored.status, stored.notes, stored.imageBytes, stored.ownerUsername);
                            String before = stored.status;
                            t.status = "Healthy".equals(before) ? "At Risk" : "Healthy";
                            t.height = t.height + 0.4;
                            check("M07 admin update accepted", trees.updateTree(t, adminToken).startsWith("SUCCESS"));
                            later(6.5, () -> {
                                check("M08 3D map shows the updated health status", t.status.equals(m.getStatusShown(id)));
                                List<String> history = trees.getTreeHistory(id, token);
                                check("M09 monitoring history records the status change", history.stream()
                                        .anyMatch(h -> h.contains("Status: " + before + " → " + t.status)));
                                check("M10 history is available to rangers", !history.isEmpty());
                                capture("3d-map-updated", scene);
                                Button showInRegistry = m.lookupAll(".button").stream()
                                        .filter(n -> n instanceof Button b && "Show in registry".equals(b.getText()))
                                        .map(n -> (Button) n).findFirst().orElseThrow();
                                showInRegistry.fire();
                                scene.getRoot().applyCss(); scene.getRoot().layout();
                                check("M11 map selection filters the registry to that tree",
                                        scene.getRoot().lookupAll(".card").size() == 1);
                                finish(0);
                            });
                        });
                    });
                });
            } catch (Throwable failure) { failure.printStackTrace(); finish(1); }
        });
    }
}
