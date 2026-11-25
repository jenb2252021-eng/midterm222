package client_GUI;

import Model.Tree;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.animation.PauseTransition;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.application.Platform;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import netscape.javascript.JSObject;

import javafx.scene.layout.StackPane;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

public final class FieldMapStage extends StackPane {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final WebEngine engine;
    private boolean htmlLoaded = false;
    private boolean mapInitialized = false;
    private final List<Tree> trees;

    private final WebView webView = new WebView();
    private final MapPreferences mapPreferences = new MapPreferences();
    public static final class MapPreferences {
        public void setOfflineTiles(boolean offline) {
            Platform.runLater(() -> ClientConfig.saveMapOfflineTiles(offline));
        }
    }
    private FieldMapStage(List<Tree> trees) {
        this.trees = trees;
        engine = webView.getEngine();
        URL page = FieldMapStage.class.getResource("/map_web/field_map.html");


        engine.getLoadWorker().stateProperty().addListener((obs, o, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                JSObject win = (JSObject) engine.executeScript("window");
                win.setMember("javaMapPrefs", mapPreferences);
                htmlLoaded = true;
                tryInitMap();
            }
        });

        getChildren().add(webView);
        sceneProperty().addListener((obs, oldScene, newScene) -> tryInitMap());
        if (page != null) engine.load(page.toExternalForm());
        widthProperty().addListener((ob, a, b) -> { tryInitMap(); pushSize(); });
        heightProperty().addListener((ob, a, b) -> { tryInitMap(); pushSize(); });
    }

    private void tryInitMap() {
        if (mapInitialized || !htmlLoaded || getScene() == null) return;
        double w = getWidth();
        double h = getHeight();
        if (w < 50 || h < 50) return;
        mapInitialized = true;

        String script = String.format(Locale.US,
                "window.initFieldMap({lat:%f,lon:%f,zoom:%d,offlineTiles:%s,tileUrlTemplate:%s,greyTileUrl:%s,containerW:%d,containerH:%d});",
                ClientConfig.getMapDefaultLat(),
                ClientConfig.getMapDefaultLon(),
                ClientConfig.getMapDefaultZoom(),
                String.valueOf(ClientConfig.useOfflineMapTiles()),
                jsQuoted(MapWebUrls.tileUrlTemplate(FieldMapStage.class)),
                jsQuoted(MapWebUrls.greyTileUrl(FieldMapStage.class)),
                (int) w, (int) h);
        engine.executeScript(script);
        pushTrees(engine, trees);

        for (int d : new int[]{200, 500, 1000, 2000}) {
            PauseTransition pt = new PauseTransition(Duration.millis(d));
            pt.setOnFinished(ev -> { tryInitMap(); pushSize(); });
            pt.play();
        }
    }

    private void pushSize() {
        if (!mapInitialized) return;
        double w = getScene() != null ? getWidth() : 0;
        double h = getScene() != null ? getHeight() : 0;
        if (w < 10 || h < 10) return;
        try {
            engine.executeScript(String.format(Locale.US,
                    "window._setContainerSize(%d,%d)", (int) w, (int) h));
        } catch (Exception ignored) {}
    }

    private static void pushTrees(WebEngine engine, List<Tree> trees) {
        try {
            ArrayNode arr = MAPPER.createArrayNode();
            for (Tree t : trees) {
                double[] ll = GeoCoordFormat.tryParse(t.location);
                if (ll == null) continue;
                ObjectNode o = MAPPER.createObjectNode();
                o.put("id", t.id);
                o.put("species", t.species != null ? t.species : "");
                o.put("status", t.status != null ? t.status : "");
                o.put("height", t.height);
                o.put("diameter", t.diameter);
                o.put("planted", t.plantedDate);
                o.put("notes", t.notes);
                o.put("lat", ll[0]);
                o.put("lon", ll[1]);
                o.put("locationLabel", t.location != null ? t.location : GeoCoordFormat.format(ll[0], ll[1]));
                if (t.imageBytes != null && t.imageBytes.length > 0) {
                    String b64 = Base64.getEncoder().encodeToString(t.imageBytes);
                    String mime = guessImageMime(t.imageBytes);
                    o.put("imageDataUrl", "data:" + mime + ";base64," + b64);
                }
                arr.add(o);
            }
            String json = MAPPER.writeValueAsString(arr);
            String b64 = Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            engine.executeScript("window.loadTreesFromB64('" + b64 + "')");
        } catch (Exception ignored) {}
    }

    private static String jsQuoted(String s) {
        if (s == null || s.isEmpty()) return "''";
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\r", " ").replace("\n", " ") + "'";
    }

    private static String guessImageMime(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (b.length >= 8 && b[0] == (byte) 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        return "image/jpeg";
    }

    public static FieldMapStage view(List<Tree> trees) {
        return new FieldMapStage(trees);
    }
}
