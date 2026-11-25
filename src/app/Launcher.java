package app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class Launcher extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(false);

        Thread discoveryThread = new Thread(() -> {
            boolean found = false;
            try {
                found = discoverServerViaReflection();
            } catch (Exception ignored) {
            }

            final boolean shouldLaunchClient = found;
            Platform.runLater(() -> {
                if (shouldLaunchClient) {
                    System.out.println("Found existing server on LAN. Launching Tree Client.");
                    launchTreeClientViaReflection();
                } else {
                    showServerLauncherViaReflection();
                }
            });
        }, "LanServerDiscovery");
        discoveryThread.setDaemon(true);
        discoveryThread.start();
    }

    private static boolean discoverServerViaReflection() {
        try {
            Class<?> treeServerClass = Class.forName("Server.TreeServer");
            Object ok = treeServerClass.getMethod("discoverServer", int.class).invoke(null, 800);
            return ok instanceof Boolean && (Boolean) ok;
        } catch (Exception e) {
            return false;
        }
    }

    private static void launchTreeClientViaReflection() {
        try {
            Class<?> treeClientClass = Class.forName("client_GUI.TreeClient");
            treeClientClass.getMethod("launchConnection").invoke(null);
        } catch (Exception e) {
            // If this fails, we can't recover cleanly here; show in console.
            e.printStackTrace();
        }
    }

    private static void showServerLauncherViaReflection() {
        try {
            Class<?> serverLauncherClass = Class.forName("client_GUI.ServerLauncherGUI");
            Object stageObj = serverLauncherClass.getConstructor().newInstance();
            ((Stage) stageObj).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

