package client_GUI;

import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import javafx.application.Platform;
import javafx.stage.Stage;
import java.lang.reflect.Constructor;

/**
 * TreeClient: The earthy gateway to the Lorax Network.
 * Logic preserved, visuals "Earthified."
 */
public class TreeClient {
    private static Registry registry;

    // --- EARTHY THEME CONSTANTS ---
    private static final String CLR_BG      = "#EBE5C5"; // Cream
    private static final String CLR_FOREST  = "#4B663F"; // Forest Green
    private static final String CLR_OLIVE   = "#45663B"; // Dark Olive
    // Sand Border
    private static final String BRDR_RAD    = "3px";      // Strict 3px curve

    public static void main(String[] args) {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}

        try {
            if (isClientFirstRunViaReflection()) {
                Thread t = new Thread(() -> {
                    boolean ok = tryAutoConfigureFromDiscovery();
                    Platform.runLater(() -> {
                        if (ok) launchConnection();
                        else showClientSetupDialogViaReflection();
                    });
                }, "Earthy-AutoConfigure");
                t.start();
            } else {
                launchConnection();
            }
        } catch (Exception ex) {
            Platform.runLater(() -> showEarthyAlert("Seed Error", "Client failed to sprout: " + ex.getMessage()));
        }
    }

    /**
     * Styles standard alerts to match the forest theme.
     */
    private static void showEarthyAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);

        DialogPane pane = alert.getDialogPane();
        pane.setStyle(
                "-fx-background-color: " + CLR_BG + ";" +
                        "-fx-border-color: " + CLR_FOREST + ";" +
                        "-fx-border-width: 2px;" +
                        "-fx-background-radius: " + BRDR_RAD + ";" +
                        "-fx-border-radius: " + BRDR_RAD + ";"
        );

        // Style the internal text
        pane.lookup(".content.label").setStyle("-fx-text-fill: " + CLR_OLIVE + "; -fx-font-weight: bold;");

        alert.showAndWait();
    }

    private static boolean tryAutoConfigureFromDiscovery() {
        try {
            Object ds = discoverServerInfoViaReflection();
            if (ds != null) {
                String ip = getDiscoveredServerIP(ds);
                int port = getDiscoveredServerPort(ds);
                if (ip != null && !ip.trim().isEmpty()) {
                    saveClientConfigViaReflection(ip, port);
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static void launchConnection() {
        System.setProperty("sun.rmi.transport.connectionTimeout", "5000");
        System.setProperty("sun.rmi.transport.tcp.responseTimeout", "5000");

        Thread t = new Thread(() -> {
            try {
                String serverIP = getClientServerIPViaReflection();
                int serverPort = getClientServerPortViaReflection();

                try {
                    registry = LocateRegistry.getRegistry(serverIP, serverPort);
                    registry.lookup("UserService");
                } catch (Exception firstFailure) {
                    System.err.println("[TreeClient] Initial connection failed: " + firstFailure);
                    Object ds = discoverServerInfoViaReflection();
                    if (ds == null) throw firstFailure;

                    String ip = getDiscoveredServerIP(ds);
                    int port = getDiscoveredServerPort(ds);
                    saveClientConfigViaReflection(ip, port);
                    registry = LocateRegistry.getRegistry(ip, port);
                    registry.lookup("UserService");
                }

                Platform.runLater(() -> showLoginGUIViaReflection(registry));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    showEarthyAlert("Connection Lost",
                            "Cannot find the Forest Server.\n" + e.getClass().getSimpleName() + ": " + e.getMessage());
                    showClientSetupDialogViaReflection();
                });
            }
        }, "Earthy-Connection");
        t.start();
    }

    // --- REFLECTION LAYER (Preserved Logic) ---

    private static boolean isClientFirstRunViaReflection() {
        try {
            Class<?> cfg = Class.forName("client_GUI.ClientConfig");
            return (Boolean) cfg.getMethod("isFirstRun").invoke(null);
        } catch (Exception e) {
            throw new RuntimeException("ClientConfig.isFirstRun() failed", e);
        }
    }

    private static String getClientServerIPViaReflection() {
        try {
            Class<?> cfg = Class.forName("client_GUI.ClientConfig");
            return (String) cfg.getMethod("getServerIP").invoke(null);
        } catch (Exception e) {
            throw new RuntimeException("ClientConfig.getServerIP() failed", e);
        }
    }

    private static int getClientServerPortViaReflection() {
        try {
            Class<?> cfg = Class.forName("client_GUI.ClientConfig");
            return (Integer) cfg.getMethod("getServerPort").invoke(null);
        } catch (Exception e) {
            throw new RuntimeException("ClientConfig.getServerPort() failed", e);
        }
    }

    private static void saveClientConfigViaReflection(String ip, int port) {
        try {
            Class<?> cfg = Class.forName("client_GUI.ClientConfig");
            cfg.getMethod("saveConfig", String.class, int.class).invoke(null, ip, port);
        } catch (Exception e) {
            throw new RuntimeException("ClientConfig.saveConfig() failed", e);
        }
    }

    private static void showClientSetupDialogViaReflection() {
        try {
            Class<?> dialogClass = Class.forName("client_GUI.ClientSetupDialog");
            Object obj = dialogClass.getConstructor().newInstance();
            if (obj instanceof Stage) {
                ((Stage) obj).show();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to show Earthy Setup Dialog", e);
        }
    }

    private static void showLoginGUIViaReflection(Registry registry) {
        try {
            Class<?> loginGuiClass = Class.forName("client_GUI.LoginGUI");
            Constructor<?> ctor = loginGuiClass.getConstructor(Registry.class);
            Object obj = ctor.newInstance(registry);
            if (obj instanceof Stage) {
                ((Stage) obj).show();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to show Earthy Login GUI", e);
        }
    }

    private static Object discoverServerInfoViaReflection() {
        try {
            Class<?> treeServerClass = Class.forName("Server.TreeServer");
            return treeServerClass.getMethod("discoverServerInfo", int.class).invoke(null, 1200);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getDiscoveredServerIP(Object discoveredServer) {
        try {
            Object ipObj = discoveredServer.getClass().getField("ip").get(discoveredServer);
            return ipObj instanceof String ? (String) ipObj : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int getDiscoveredServerPort(Object discoveredServer) {
        try {
            Object portObj = discoveredServer.getClass().getField("port").get(discoveredServer);
            if (portObj instanceof Number) return ((Number) portObj).intValue();
            return 0;
        } catch (Exception ignored) {
            return 0;
        }
    }
}