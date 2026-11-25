package client_GUI;

import java.net.URL;

/**
 * Resolves classpath URLs for map tiles so JavaFX WebView can load them from jar: URLs.
 * Relative paths like {@code tiles/{z}/{x}/{y}.png} often fail when the HTML is loaded from a jar.
 */
public final class MapWebUrls {

    private MapWebUrls() {}

    /**
     * Leaflet tile URL template, e.g. {@code jar:file:/...!/map_web/tiles/{z}/{x}/{y}.png}
     */
    public static String tileUrlTemplate(Class<?> anchor) {
        try {
            URL html = anchor.getResource("/map_web/field_map.html");
            if (html == null) {
                html = anchor.getResource("/map_web/picker.html");
            }
            if (html == null) {
                return "";
            }
            URL tilesDir = new URL(html, "tiles/");
            return tilesDir.toExternalForm() + "{z}/{x}/{y}.png";
        } catch (Exception e) {
            return "";
        }
    }

    public static String greyTileUrl(Class<?> anchor) {
        try {
            URL html = anchor.getResource("/map_web/field_map.html");
            if (html == null) {
                html = anchor.getResource("/map_web/picker.html");
            }
            if (html == null) {
                return "";
            }
            return new URL(html, "leaflet/grey_tile.png").toExternalForm();
        } catch (Exception e) {
            return "";
        }
    }
}
