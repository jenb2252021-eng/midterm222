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
 * Checks the administration pages and their server features against a temporary copy of the demonstration data:
 * Messages (two panels, unread counts, send), Pending Review, Manage Users, Archive (restore and permanent delete),
 * Ranger Access and System States. Screenshots go to docs/screens/admin-*.png.
 */
public class AdminScreens {
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
    interface Step { void run() throws Exception; }

    static void capture(String name, Scene scene) throws Exception {
        WritableImage image = scene.snapshot(null);
        int w = (int) image.getWidth(), h = (int) image.getHeight();
        int[] px = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), px, 0, w);
        BufferedImage png = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        png.setRGB(0, 0, w, h, px, 0, w);
        ImageIO.write(png, "png", destination.resolve(name + ".png").toFile());
        System.out.println("  screenshot " + name + ".png");
    }
    static void navigate(Scene scene, String name) {
        scene.getRoot().applyCss();
        scene.getRoot().lookupAll(".nav").stream().filter(n -> n instanceof Button b && name.equals(b.getText()))
                .map(n -> (Button) n).findFirst().orElseThrow().fire();
        scene.getRoot().applyCss();
        scene.getRoot().layout();
    }
    static void check(String id, boolean ok) {
        if (!ok) throw new AssertionError(id);
        passed++;
        System.out.println("PASS  " + id);
    }
    static void finish(int code) {
        try (var files = Files.walk(isolated)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (Exception ex) { code = 1; }
        if (code == 0) System.out.println(passed + " checks passed.");
        System.exit(code);
    }
    static void later(double s, Step step) {
        PauseTransition p = new PauseTransition(Duration.seconds(s));
        p.setOnFinished(e -> { try { step.run(); } catch (Throwable t) { t.printStackTrace(); finish(1); } });
        p.play();
    }
    static <T extends Node> List<T> all(Scene s, String css, Class<T> type) {
        s.getRoot().applyCss();
        return s.getRoot().lookupAll(css).stream().filter(type::isInstance).map(type::cast).toList();
    }

    public static void main(String[] args) throws Exception {
        destination = Files.createDirectories(Path.of("docs", "screens"));
        isolated = Files.createTempDirectory("lorax-admin-");
        Files.copy(Path.of("demo-data", "tree_data.json"), isolated.resolve("tree_data.json"));
        System.setProperty("lorax.data.dir", isolated.toString());
        Path config = isolated.resolve("client.properties");
        Files.writeString(config, "map.offline.tiles=true");
        System.setProperty("lorax.client.config", config.toString());
        String pw = UUID.randomUUID().toString();
        UserJSONUtil.createLocalAdministrator(pw);
        UserService users = new UserServiceImpl();
        TreeService trees = new TreeServiceImpl();
        ChatService chat = new ChatServiceImpl();
        services.put("UserService", users); services.put("TreeService", trees);
        services.put("AdminService", new AdminServiceImpl()); services.put("ChatService", chat);
        users.register("ranger_ana", pw);
        users.register("ranger_ben", pw);
        String adminToken = users.authenticate("admin", pw).substring(9);
        String ana = users.authenticate("ranger_ana", pw).substring(9);

        // Server features first.
        chat.sendMessage(ana, "admin", "Exposed roots near the eastern slope.");
        chat.sendMessage(ana, "admin", "Photo to follow.");
        check("A01 unread counts per sender", chat.getUnreadCountsBySender(adminToken).getOrDefault("ranger_ana", 0) == 2);
        Tree demo = trees.getAllTrees(adminToken).get(0);
        int archivedId = demo.id;
        check("A02 delete moves a tree to the archive", trees.deleteTree(archivedId, adminToken).startsWith("SUCCESS")
                && trees.getDeletedTrees(adminToken).stream().anyMatch(t -> t.id == archivedId));
        check("A03 rangers cannot restore", trees.restoreDeletedTree(archivedId, ana).startsWith("ERROR"));
        check("A04 restore returns the tree with its ID", trees.restoreDeletedTree(archivedId, adminToken).startsWith("SUCCESS")
                && trees.getAllTrees(adminToken).stream().anyMatch(t -> t.id == archivedId)
                && trees.getDeletedTrees(adminToken).stream().noneMatch(t -> t.id == archivedId));
        check("A05 restore appears in the tree history", trees.getTreeHistory(archivedId, adminToken).stream().anyMatch(h -> h.contains("Restored from the archive")));
        trees.deleteTree(archivedId, adminToken);
        Tree second = trees.getAllTrees(adminToken).get(0);
        trees.deleteTree(second.id, adminToken);
        check("A06 permanent delete empties that archive entry", trees.purgeDeletedTree(second.id, adminToken).startsWith("SUCCESS")
                && trees.getDeletedTrees(adminToken).stream().noneMatch(t -> t.id == second.id));
        trees.addTree(new Tree(0, "Benguet Pine", "16.405700,120.598500", 1.8, 3.2, "2025-06-14", "Monitoring", "Inspect new needles.", null, null), ana);

        Platform.startup(() -> {
            try {
                Platform.setImplicitExit(false);
                TreeDashboard dash = new TreeDashboard(directory, "admin", adminToken);
                dash.show();
                Scene scene = dash.getScene();
                later(1.5, () -> {
                    navigate(scene, "Messages");
                    check("A07 messages is a two-panel view", ((BorderPane) scene.getRoot()).getCenter() instanceof SplitPane sp && sp.getItems().size() == 2);
                    later(2.5, () -> {
                        ListView<?> people = all(scene, ".people-list", ListView.class).get(0);
                        check("A08 people list shows the other accounts", people.getItems().size() == 2);
                        check("A09 unread badge shown", !all(scene, ".unread-badge", Label.class).isEmpty());
                        capture("admin-messages-list", scene);
                        ((ListView<String>) people).getSelectionModel().select("ranger_ana");
                        later(2.5, () -> {
                            check("A10 conversation shows both messages", all(scene, ".bubble-theirs", Node.class).size() == 2);
                            TextArea input = all(scene, ".composer-input", TextArea.class).get(0);
                            input.setText("Thanks, I'll check it tomorrow.");
                            all(scene, ".button", Button.class).stream().filter(b -> "Send".equals(b.getText())).findFirst().orElseThrow().fire();
                            later(2.5, () -> {
                                check("A11 sent message delivered", chat.getConversation(ana, "admin").stream().anyMatch(m -> m.content != null && m.content.startsWith("Thanks")));
                                check("A12 sent bubble shown with state", all(scene, ".bubble-mine", Node.class).size() == 1);
                                capture("admin-messages", scene);
                                navigate(scene, "Pending Review");
                                ListView<?> queue = (ListView<?>) scene.getRoot().lookup(".list-view");
                                check("A13 pending queue lists the submission", queue.getItems().size() == 1);
                                later(2, () -> {
                                    capture("admin-pending", scene);
                                    navigate(scene, "Manage Users");
                                    later(2.5, () -> {
                                        TableView<?> table = all(scene, ".inventory-table", TableView.class).get(0);
                                        check("A14 user table lists every account", table.getItems().size() == 3);
                                        capture("admin-users", scene);
                                        navigate(scene, "Archive");
                                        later(2.5, () -> {
                                            TableView<?> arch = all(scene, ".inventory-table", TableView.class).get(0);
                                            check("A15 archive table shows archived records", arch.getItems().size() == 1);
                                            check("A16 archive offers restore", all(scene, ".button", Button.class).stream().anyMatch(b -> b.getText().startsWith("Restore")));
                                            capture("admin-archive", scene);
                                            navigate(scene, "Ranger Access");
                                            later(2, () -> {
                                                check("A17 ranger access shows the state", all(scene, ".access-state", Label.class).stream().anyMatch(l -> l.getText().contains("open")));
                                                capture("admin-ranger-access", scene);
                                                navigate(scene, "System States");
                                                later(2, () -> {
                                                    TableView<?> log = all(scene, ".inventory-table", TableView.class).get(0);
                                                    check("A18 activity log table has events", log.getItems().size() > 5);
                                                    capture("admin-system-states", scene);
                                                    finish(0);
                                                });
                                            });
                                        });
                                    });
                                });
                            });
                        });
                    });
                });
            } catch (Throwable t) { t.printStackTrace(); finish(1); }
        });
    }
}
