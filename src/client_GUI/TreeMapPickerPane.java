package client_GUI;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.Window;
import netscape.javascript.JSObject;

import java.net.URL;
import java.util.Locale;

/**
 * Leaflet (offline-capable) or Google Maps picker embedded in a {@link WebView}.
 */
public class TreeMapPickerPane extends StackPane {

    private static final String BANNER_AFTER_PIN =
            "Coordinates saved. Warning: trees placed in water or added using only the map may be deleted by an administrator.";

    private final TextField coordField;
    private final WebView webView = new WebView();
    private final WebEngine engine = webView.getEngine();
    private final MapPickerBridge mapPickerBridge;
    private final Object mapPrefsBridge;
    private volatile boolean bridgeReady;

    private final ChangeListener<Window> windowInSceneListener = (obs, oldWin, newWin) -> {
        unbindWindowLayout();
        if (newWin != null) {
            bindWindowLayout(newWin);
        }
        notifyJsMapLayout();
    };

    private final ChangeListener<Scene> sceneListener = (obs, oldScene, newScene) -> {
        if (oldScene != null) {
            oldScene.windowProperty().removeListener(windowInSceneListener);
        }
        unbindWindowLayout();
        if (newScene != null) {
            newScene.windowProperty().addListener(windowInSceneListener);
            Window w = newScene.getWindow();
            if (w != null) {
                bindWindowLayout(w);
            }
        }
        notifyJsMapLayout();
    };

    private ChangeListener<Number> windowSizeListener;
    private ChangeListener<Boolean> windowMaximizedListener;
    private Window boundWindow;

    private boolean htmlLoaded = false;
    private boolean mapInitialized = false;

    public TreeMapPickerPane(TextField coordField) {
        this.coordField = coordField;
        this.mapPickerBridge = new MapPickerBridge(this);
        this.mapPrefsBridge = new Object() {
            @SuppressWarnings("unused")
            public void setOfflineTiles(boolean offline) {
                Platform.runLater(() -> ClientConfig.saveMapOfflineTiles(offline));
            }
        };
        webView.setMinSize(200, 160);
        webView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        webView.prefWidthProperty().bind(widthProperty());
        webView.prefHeightProperty().bind(heightProperty());
        getChildren().add(webView);

        sceneProperty().addListener(sceneListener);

        URL page = TreeMapPickerPane.class.getResource("/map_web/picker.html");
        if (page != null) {
            engine.load(page.toExternalForm());
        }

        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                JSObject win = (JSObject) engine.executeScript("window");
                win.setMember("javaPicker", mapPickerBridge);
                win.setMember("javaMapPrefs", mapPrefsBridge);
                bridgeReady = true;
                htmlLoaded = true;
                tryInitMap();
            }
        });
        widthProperty().addListener((o, a, b) -> {
            pushSize();
            notifyJsMapLayout();
        });
        heightProperty().addListener((o, a, b) -> {
            pushSize();
            notifyJsMapLayout();
        });
    }

    private void tryInitMap() {
        if (mapInitialized || !htmlLoaded || !bridgeReady) return;
        double w = getWidth();
        double h = getHeight();
        if (w < 50 || h < 50) {
            javafx.animation.PauseTransition retry = new javafx.animation.PauseTransition(javafx.util.Duration.millis(100));
            retry.setOnFinished(ev -> tryInitMap());
            retry.play();
            return;
        }
        mapInitialized = true;
        runInitScript();
        syncPinFromCoordinates();
        notifyJsMapLayout();

        for (int d : new int[]{200, 500, 1000, 2000}) {
            javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(javafx.util.Duration.millis(d));
            pt.setOnFinished(ev -> pushSize());
            pt.play();
        }
    }

    private void pushSize() {
        if (!mapInitialized) return;
        double w = getWidth();
        double h = getHeight();
        if (w < 10 || h < 10) return;
        Platform.runLater(() -> {
            try {
                engine.executeScript(String.format(Locale.US,
                        "window._setContainerSize(%d,%d)", (int) w, (int) h));
            } catch (Exception ignored) {}
        });
    }

    private void bindWindowLayout(Window w) {
        if (w == boundWindow) {
            return;
        }
        unbindWindowLayout();
        boundWindow = w;
        windowSizeListener = (o, a, b) -> notifyJsMapLayout();
        w.widthProperty().addListener(windowSizeListener);
        w.heightProperty().addListener(windowSizeListener);
        if (w instanceof Stage) {
            windowMaximizedListener = (o, a, b) -> notifyJsMapLayout();
            ((Stage) w).maximizedProperty().addListener(windowMaximizedListener);
        }
    }

    private void unbindWindowLayout() {
        if (boundWindow != null) {
            if (windowSizeListener != null) {
                boundWindow.widthProperty().removeListener(windowSizeListener);
                boundWindow.heightProperty().removeListener(windowSizeListener);
            }
            if (windowMaximizedListener != null && boundWindow instanceof Stage) {
                ((Stage) boundWindow).maximizedProperty().removeListener(windowMaximizedListener);
            }
        }
        boundWindow = null;
        windowSizeListener = null;
        windowMaximizedListener = null;
    }

    /**
     * Ask Leaflet/Google inside the WebView to re-measure the map (critical after maximize / offline tile swap).
     */
    private void notifyJsMapLayout() {
        if (!bridgeReady) {
            return;
        }
        Platform.runLater(() -> {
            try {
                engine.executeScript(
                        "(function(){ if (window.scheduleMapInvalidate) window.scheduleMapInvalidate(); })()");
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Call after the dialog is shown so the map gets correct dimensions before the first click.
     */
    public void refreshMapLayout() {
        notifyJsMapLayout();
        Platform.runLater(() -> notifyJsMapLayout());
    }

    public void dispose() {
        sceneProperty().removeListener(sceneListener);
        Scene s = getScene();
        if (s != null) {
            s.windowProperty().removeListener(windowInSceneListener);
        }
        unbindWindowLayout();
    }

    public void syncPinFromCoordinates() {
        if (!bridgeReady) {
            return;
        }
        double[] ll = GeoCoordFormat.tryParse(coordField.getText());
        if (ll != null) {
            engine.executeScript(String.format(Locale.US, "window.showPin(%f,%f)", ll[0], ll[1]));
        }
        notifyJsMapLayout();
    }

    private void runInitScript() {
        String key = jsString(ClientConfig.getGoogleMapsApiKey());
        boolean useGoogle = !ClientConfig.getGoogleMapsApiKey().isEmpty();
        boolean offlineTiles = ClientConfig.useOfflineMapTiles();
        double lat = ClientConfig.getMapDefaultLat();
        double lon = ClientConfig.getMapDefaultLon();
        int zoom = ClientConfig.getMapDefaultZoom();
        int cw = (int) getWidth();
        int ch = (int) getHeight();
        String script = String.format(Locale.US,
                "window.initPicker({lat:%f,lon:%f,zoom:%d,useGoogle:%s,apiKey:%s,offlineTiles:%s,tileUrlTemplate:%s,greyTileUrl:%s,containerW:%d,containerH:%d});",
                lat, lon, zoom, useGoogle, key, offlineTiles,
                jsString(MapWebUrls.tileUrlTemplate(TreeMapPickerPane.class)),
                jsString(MapWebUrls.greyTileUrl(TreeMapPickerPane.class)),
                cw, ch);
        engine.executeScript(script);
    }

    private static String jsString(String s) {
        if (s == null) {
            return "''";
        }
        String escaped = s.replace("\\", "\\\\").replace("'", "\\'").replace("\r", " ").replace("\n", " ");
        return "'" + escaped + "'";
    }

    /**
     * Static bridge so WebView JS can invoke methods reliably (non-static inner classes can fail on some JDKs).
     */
    public static final class MapPickerBridge {
        private final TreeMapPickerPane pane;

        MapPickerBridge(TreeMapPickerPane pane) {
            this.pane = pane;
        }

        /**
         * Preferred path from the map script: passes {@code "lat,lon"} so the WebView bridge never relies on
         * {@code double} JNI coercion (which can fail silently in {@code try/catch} on the JS side).
         */
        @SuppressWarnings("unused")
        public void applyPinCoordinates(String coords) {
            if (coords == null) {
                return;
            }
            final String c = coords.trim();
            if (GeoCoordFormat.tryParse(c) == null) {
                return;
            }
            Runnable apply = () -> {
                pane.coordField.setText(c);
                try {
                    pane.engine.executeScript("window.setBanner(" + TreeMapPickerPane.jsString(BANNER_AFTER_PIN) + ")");
                    pane.engine.executeScript(
                            "(function(){ if (window.scheduleMapInvalidate) window.scheduleMapInvalidate(); })()");
                } catch (Exception ignored) {
                }
            };
            if (Platform.isFxApplicationThread()) {
                apply.run();
            } else {
                Platform.runLater(apply);
            }
        }

        /** Fallback if a caller still uses numeric args; delegates to {@link #applyPinCoordinates(String)}. */
        @SuppressWarnings("unused")
        public void onPinProposed(Object latObj, Object lngObj) {
            double lat = asDouble(latObj);
            double lng = asDouble(lngObj);
            if (!Double.isFinite(lat) || !Double.isFinite(lng)) {
                return;
            }
            applyPinCoordinates(GeoCoordFormat.format(lat, lng));
        }

        private static double asDouble(Object o) {
            if (o == null) {
                return Double.NaN;
            }
            if (o instanceof Number) {
                return ((Number) o).doubleValue();
            }
            try {
                return Double.parseDouble(o.toString());
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
    }
}
