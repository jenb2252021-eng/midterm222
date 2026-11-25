package client_GUI;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Manages client configuration for server connection.
 * Stores server IP and port in a properties file.
 * Shows setup dialog on first run, then uses saved configuration.
 */
public class ClientConfig {
    private static final String CONFIG_FILE = System.getProperty("lorax.client.config", "client_config.properties");
    private static final String SERVER_IP_KEY = "server.ip";
    private static final String SERVER_PORT_KEY = "server.port";
    private static final String GOOGLE_MAPS_KEY = "google.maps.api.key";
    private static final String MAP_OFFLINE_TILES = "map.offline.tiles";
    private static final String MAP_DEFAULT_LAT = "map.default.lat";
    private static final String MAP_DEFAULT_LON = "map.default.lon";
    private static final String MAP_DEFAULT_ZOOM = "map.default.zoom";
    
    private static String serverIP;
    private static int serverPort;
    private static String googleMapsApiKey = "";
    private static boolean mapOfflineTiles;
    private static double mapDefaultLat = 16.3933;
    private static double mapDefaultLon = 120.5960;
    private static int mapDefaultZoom = 14;
    private static boolean isFirstRun;

    static {
        loadConfig();
    }

    private static void loadConfig() {
        Properties props = new Properties();
        File configFile = new File(CONFIG_FILE);
        
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                serverIP = props.getProperty(SERVER_IP_KEY, "localhost");
                serverPort = Integer.parseInt(props.getProperty(SERVER_PORT_KEY, "1099"));
                googleMapsApiKey = props.getProperty(GOOGLE_MAPS_KEY, "").trim();
                mapOfflineTiles = Boolean.parseBoolean(props.getProperty(MAP_OFFLINE_TILES, "false"));
                mapDefaultLat = parseDouble(props.getProperty(MAP_DEFAULT_LAT, "16.3933"), 16.3933);
                mapDefaultLon = parseDouble(props.getProperty(MAP_DEFAULT_LON, "120.5960"), 120.5960);
                mapDefaultZoom = parseInt(props.getProperty(MAP_DEFAULT_ZOOM, "14"), 14);
                isFirstRun = false;
            } catch (IOException | NumberFormatException e) {
                setDefaults();
                isFirstRun = true;
            }
        } else {
            setDefaults();
            isFirstRun = true;
        }
    }

    private static double parseDouble(String s, double dflt) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return dflt;
        }
    }

    private static int parseInt(String s, int dflt) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return dflt;
        }
    }

    /**
     * Set default values for first run
     */
    private static void setDefaults() {
        serverIP = "localhost";
        serverPort = 1099;
        googleMapsApiKey = "";
        mapOfflineTiles = false;
        mapDefaultLat = 16.3933;
        mapDefaultLon = 120.5960;
        mapDefaultZoom = 14;
    }

    /**
     * Save configuration to file
     */
    public static void saveConfig(String ip, int port) {
        Properties props = new Properties();
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            } catch (IOException ignored) {
            }
        }
        props.setProperty(SERVER_IP_KEY, ip);
        props.setProperty(SERVER_PORT_KEY, String.valueOf(port));
        
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "Tree Management System - Client Configuration");
            serverIP = ip;
            serverPort = port;
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    /**
     * Persists whether Leaflet uses online OSM tiles or local {@code map_web/tiles/{z}/{x}/{y}.png}.
     * Merges into the existing config file so server IP/port and other keys are preserved.
     */
    public static void saveMapOfflineTiles(boolean offline) {
        Properties props = new Properties();
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            } catch (IOException ignored) {
            }
        }
        props.setProperty(MAP_OFFLINE_TILES, String.valueOf(offline));
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "Tree Management System - Client Configuration");
            mapOfflineTiles = offline;
        } catch (IOException e) {
            System.err.println("Failed to save map tile mode: " + e.getMessage());
        }
    }

    /**
     * Get the server IP from configuration
     */
    public static String getServerIP() {
        return serverIP;
    }

    /**
     * Get the server port from configuration
     */
    public static int getServerPort() {
        return serverPort;
    }

    /**
     * Check if this is the first run (no config file exists)
     */
    public static boolean isFirstRun() {
        return isFirstRun;
    }

    public static String getGoogleMapsApiKey() {
        return googleMapsApiKey != null ? googleMapsApiKey : "";
    }

    public static boolean useOfflineMapTiles() {
        return mapOfflineTiles;
    }

    public static double getMapDefaultLat() {
        return mapDefaultLat;
    }

    public static double getMapDefaultLon() {
        return mapDefaultLon;
    }

    public static int getMapDefaultZoom() {
        return mapDefaultZoom;
    }

}
