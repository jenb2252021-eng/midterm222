import Server.*;
import client_GUI.*;
import JSONUtil.UserJSONUtil;
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

/** Renders the actual JavaFX scenes against a temporary copy of the demonstration data. */
public class RegistryScreens {
    static Path destination;
    static Path isolated;
    static final Map<String,Remote> services = new HashMap<>();
    static final Registry directory = new Registry() {
        public Remote lookup(String name) { return services.get(name); }
        public String[] list() { return services.keySet().toArray(String[]::new); }
        public void bind(String n,Remote r) { throw new UnsupportedOperationException(); }
        public void rebind(String n,Remote r) { throw new UnsupportedOperationException(); }
        public void unbind(String n) { throw new UnsupportedOperationException(); }
    };
    static void capture(String name, Scene scene) throws Exception {
        WritableImage image = scene.snapshot(null);
        int w = (int)image.getWidth(), h = (int)image.getHeight();
        int[] pixels = new int[w*h];
        image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),pixels,0,w);
        BufferedImage png = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        png.setRGB(0,0,w,h,pixels,0,w);
        ImageIO.write(png,"png",destination.resolve(name+".png").toFile());
        System.out.println(name+" "+w+"x"+h);
    }
    static void navigate(Scene scene, String name) {
        scene.getRoot().applyCss();
        scene.getRoot().lookupAll(".nav").stream()
                .filter(n -> n instanceof Button button && name.equals(button.getText()))
                .map(n -> (Button)n).findFirst().orElseThrow().fire();
        scene.getRoot().applyCss();
        scene.getRoot().layout();
    }
    static void check(String id, boolean result) {
        if (!result) throw new AssertionError(id);
        System.out.println("PASS  " + id);
    }
    static void finish(int code) {
        try (var files = Files.walk(isolated)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (Exception ex) { ex.printStackTrace(); code = 1; }
        System.exit(code);
    }
    public static void main(String[] args) throws Exception {
        destination = Files.createDirectories(Path.of("docs","screens"));
        isolated = Files.createTempDirectory("lorax-screens-");
        Files.copy(Path.of("demo-data","tree_data.json"),isolated.resolve("tree_data.json"));
        System.setProperty("lorax.data.dir",isolated.toString());
        Path config = isolated.resolve("client.properties");
        Files.writeString(config,"map.offline.tiles=true");
        System.setProperty("lorax.client.config",config.toString());
        String pw=UUID.randomUUID().toString();
        UserJSONUtil.createLocalAdministrator(pw);
        UserService users=new UserServiceImpl();
        services.put("UserService",users); services.put("TreeService",new TreeServiceImpl());
        services.put("AdminService",new AdminServiceImpl()); services.put("ChatService",new ChatServiceImpl());
        users.register("ranger_ana",pw);
        String adminToken = users.authenticate("admin",pw).substring(9);
        String token=users.authenticate("ranger_ana",pw).substring(9);
        Platform.startup(()-> {
            try {
                Platform.setImplicitExit(false);
                capture("sign-in",new LoginGUI(directory).getScene());
                TreeDashboard dashboard = new TreeDashboard(directory,"ranger_ana",token);
                PauseTransition delay=new PauseTransition(Duration.seconds(1));
                delay.setOnFinished(e -> {
                    try {
                        Scene scene=dashboard.getScene();
                        capture("forest-registry",scene);
                        long cards=scene.getRoot().lookupAll(".card").size();
                        if(cards != 4) throw new AssertionError("Expected four demonstration trees, found "+cards);
                        System.out.println("PASS  UI01 four demonstration cards rendered");
                        TextField search = (TextField)scene.getRoot().lookup(".text-field");
                        search.setText("#4656");
                        check("UI02 inventory ID search", scene.getRoot().lookupAll(".card").size() == 1);
                        search.clear();
                        navigate(scene,"Messages");
                        check("UI03 messaging workspace", ((BorderPane)scene.getRoot()).getCenter() instanceof SplitPane);
                        navigate(scene,"Profile");
                        check("UI04 profile workspace", ((BorderPane)scene.getRoot()).getCenter().getClass().getSimpleName().equals("ProfileDialog"));
                        dashboard.show();
                        navigate(scene,"3D Map");
                        TreeMap3D map = (TreeMap3D)((BorderPane)scene.getRoot()).getCenter();
                        PauseTransition mapDelay = new PauseTransition(Duration.seconds(4));
                        mapDelay.setOnFinished(event -> {
                            try {
                                map.applyCss();
                                ToggleButton offline = (ToggleButton)map.lookup("#btnTilesOffline");
                                check("UI05 four trees on the offline 3D forest map", offline != null && offline.isSelected()
                                        && map.getRenderedTreeCount() == 4);
                                boolean placed = true;
                                for (Model.Tree t : ((TreeService)services.get("TreeService")).getAllTrees(token)) {
                                    String[] ll = t.location.split(",");
                                    double[] want = map.project(Double.parseDouble(ll[0]), Double.parseDouble(ll[1]));
                                    double[] got = map.getScenePosition(t.id);
                                    placed &= got != null && Math.abs(got[0]-want[0]) < 0.01 && Math.abs(got[1]-want[1]) < 0.01;
                                }
                                check("UI09 each 3D tree stands at its recorded coordinates", placed);
                                capture("forest-map", scene);
                                @SuppressWarnings("unchecked")
                                ComboBox<String> status = (ComboBox<String>)map.lookup(".combo-box");
                                status.setValue("At Risk");
                                ListView<?> list = map.lookupAll(".list-view").stream()
                                        .filter(n -> n instanceof ListView<?> l && !l.getItems().isEmpty() && l.getItems().get(0) instanceof Model.Tree)
                                        .map(n -> (ListView<?>)n).findFirst().orElseThrow();
                                check("UI06 map status filter", list.getItems().size() == 1);
                                status.setValue("All statuses");
                                navigate(scene,"Registry");
                                check("UI07 return to inventory", scene.getRoot().lookupAll(".card").size() == 4);
                                var trees = (TreeService)services.get("TreeService");
                                Model.Tree entry = new Model.Tree(0,"Benguet Pine","16.405700,120.598500",1.8,3.2,
                                        "2025-06-14","Monitoring","Inspect new needles.",null,null);
                                trees.addTree(entry,token);
                                Scene admin = new TreeDashboard(directory,"admin",adminToken).getScene();
                                navigate(admin,"Pending Review");
                                ListView<?> queue = (ListView<?>)admin.getRoot().lookup(".list-view");
                                check("UI08 selected submission ready for administrator review", queue.getItems().size() == 1
                                        && admin.getRoot().lookupAll(".primary").stream()
                                        .anyMatch(n -> n instanceof Button b && "Approve".equals(b.getText())));
                                capture("pending-review",admin);
                                finish(0);
                            } catch(Throwable failure) { failure.printStackTrace(); finish(1); }
                        });
                        mapDelay.play();
                    } catch(Throwable failure) { failure.printStackTrace(); finish(1); }
                });
                delay.play();
            } catch(Throwable failure) { failure.printStackTrace(); finish(1); }
        });
    }
}
