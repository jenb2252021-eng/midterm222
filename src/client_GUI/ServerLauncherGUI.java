package client_GUI;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import Server.TreeServer;

import java.io.File;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class ServerLauncherGUI extends Stage {
    // --- Advanced Botanical Palette ---
    private final String COLOR_BG      = "#f5f7f5"; // Light Moss
    private final String COLOR_WOOD    = "#244632"; // Bark Brown
    private final String COLOR_LEAF    = "#20865b"; // Forest Green
    private final String COLOR_RED     = "#B43C3C"; // Warning/Stopped
    private final String COLOR_ACCENT  = "#E0DDCF"; // Muted Border

    private Label lblEngineStatus;
    private TextArea txtServerInfo;
    private TextField txtAdvertisedHost;
    private Button btnStart;
    private Button btnStop;
    private Button btnContinue;
    private String adminUsername = null;

    public ServerLauncherGUI() {
        setTitle("The Lorax - Core Engine Control");
        setResizable(true);
        setWidth(520);
        setHeight(620);
        setMinWidth(420);
        setMinHeight(520);

        initUI();

        setOnCloseRequest(_ -> {
            Platform.exit();
            System.exit(0);
        });

        Platform.runLater(this::promptAdminLogin);
    }

    private void initUI() {
        VBox root = new VBox(0);
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color: " + COLOR_BG + ";");

        // 1. Header Section
        VBox header = new VBox(5);
        header.setPadding(new Insets(30, 20, 20, 20));
        header.setAlignment(Pos.CENTER);

        Label icon = new Label("The Lorax");
        icon.setFont(Font.font(35));

        Label title = new Label("Local server");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        title.setTextFill(Color.valueOf(COLOR_WOOD));

        Label subtitle = new Label("BAGUIO FOREST REGISTRY");
        subtitle.setFont(Font.font("System", FontWeight.NORMAL, 10));
        subtitle.setTextFill(Color.valueOf(COLOR_LEAF));
        header.getChildren().addAll(icon, title, subtitle);

        // 2. The Command Card
        VBox card = new VBox(15);
        card.setMaxWidth(440);
        card.setPadding(new Insets(30));
        // Parchment Card
        String COLOR_PAPER = "#ffffff";
        card.setStyle(
                "-fx-background-color: " + COLOR_PAPER + ";" +
                        "-fx-background-radius: 10 40 10 40;" + // Reversed asymmetric corners
                        "-fx-border-color: " + COLOR_ACCENT + ";" +
                        "-fx-border-radius: 10 40 10 40;" +
                        "-fx-border-width: 1.5;"
        );

        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.web("#A3B18A", 0.3));
        shadow.setRadius(15);
        shadow.setOffsetY(8);
        card.setEffect(shadow);

        // Advertised Host Panel
        VBox advPanel = new VBox(5);
        Label advLabel = createFieldLabel();
        txtAdvertisedHost = new TextField();
        txtAdvertisedHost.setDisable(true);
        styleField(txtAdvertisedHost);
        try {
            txtAdvertisedHost.setText(getActualNetworkIP());
        } catch (Exception ignored) {}
        advPanel.getChildren().addAll(advLabel, txtAdvertisedHost);

        // Status Area
        lblEngineStatus = new Label("	 SYSTEM OFFLINE");
        lblEngineStatus.setFont(Font.font("System", FontWeight.BOLD, 14));
        lblEngineStatus.setTextFill(Color.valueOf(COLOR_RED));

        txtServerInfo = new TextArea();
        txtServerInfo.setEditable(false);
        txtServerInfo.setPrefRowCount(4);
        txtServerInfo.setStyle(
                "-fx-control-inner-background: #FAF9F0;" +
                        "-fx-font-family: 'Consolas';" +
                        "-fx-font-size: 11px;" +
                        "-fx-background-radius: 5;" +
                        "-fx-border-color: " + COLOR_ACCENT + ";"
        );

        // Action Buttons
        HBox buttonRow = new HBox(15);
        buttonRow.setAlignment(Pos.CENTER);

        btnStart = new Button("Start server");
        btnStop = new Button("Stop server");
        styleAdminButton(btnStart, COLOR_LEAF);
        styleAdminButton(btnStop, COLOR_RED);
        btnStart.setDisable(true);
        btnStop.setDisable(true);

        Button btnFirewall = new Button("Configure firewall");
        btnFirewall.setMaxWidth(Double.MAX_VALUE);
        btnFirewall.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-border-color: " + COLOR_ACCENT + ";" +
                        "-fx-border-radius: 6;" +
                        "-fx-text-fill: " + COLOR_WOOD + ";" +
                        "-fx-font-size: 11px;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 6 10;"
        );
        btnFirewall.setOnAction(_ -> configureFirewall());

        setupButtonActions();
        buttonRow.getChildren().addAll(btnStart, btnStop);

        card.getChildren().addAll(advPanel, lblEngineStatus, txtServerInfo, buttonRow, btnFirewall);

        // 3. Footer Navigation
        VBox footer = new VBox(10);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(25));

        btnContinue = new Button("Connect to registry");
        btnContinue.setPrefWidth(350);
        btnContinue.setPrefHeight(45);
        btnContinue.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-border-color: " + COLOR_LEAF + ";" +
                        "-fx-border-radius: 10;" +
                        "-fx-text-fill: " + COLOR_LEAF + ";" +
                        "-fx-font-weight: bold;" +
                        "-fx-cursor: hand;"
        );
        btnContinue.setOnAction(_ -> handleContinue());

        footer.getChildren().add(btnContinue);
        root.getChildren().addAll(header, card, footer);

        Scene scene = new Scene(root);
        setScene(scene);
        refreshStatusLabel();
    }

    private void styleField(TextField f) {
        f.setStyle(
                "-fx-background-color: white;" +
                        "-fx-border-color: " + COLOR_ACCENT + ";" +
                        "-fx-border-radius: 5;" +
                        "-fx-padding: 5;"
        );
    }

    private Label createFieldLabel() {
        Label l = new Label("ADVERTISED NETWORK ADDRESS");
        l.setFont(Font.font("System", FontWeight.BOLD, 10));
        // Sage Accent
        String COLOR_MOSS = "#90A955";
        l.setTextFill(Color.valueOf(COLOR_MOSS));
        return l;
    }

    private void styleAdminButton(Button b, String color) {
        b.setPrefWidth(160);
        b.setPrefHeight(40);
        b.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 8;" +
                        "-fx-cursor: hand;"
        );
    }

    private void refreshStatusLabel() {
        boolean running = treeServerIsRunningViaReflection();
        if (running) {
            lblEngineStatus.setText("Server online");
            lblEngineStatus.setTextFill(Color.valueOf(COLOR_LEAF));
            String ip = System.getProperty("java.rmi.server.hostname");
            if (ip == null || ip.isEmpty()) {
                try { ip = getActualNetworkIP(); } catch (Exception e) { ip = "Error resolving"; }
            }
            txtServerInfo.setText("STATUS: Active\nIP: " + ip + "\nPORT: " + treeServerGetPortViaReflection() + "\nTRANSPORT: Local network (RMI)");
        } else {
            lblEngineStatus.setText("	 Server offline");
            lblEngineStatus.setTextFill(Color.valueOf(COLOR_RED));
            txtServerInfo.setText("STATUS: Offline\nStanding by for administrative authorization...");
        }
    }

    private void promptAdminLogin() {
        AdminLoginDialog dlg = new AdminLoginDialog(this);
        dlg.setOnHidden(_ -> {
            if (dlg.isAuthenticated()) {
                adminUsername = dlg.getUsername();
                setTitle("The Lorax - Admin Session [" + adminUsername + "]");
                enableServerControls(true);
                btnContinue.setText("Open registry");
                btnContinue.setStyle(btnContinue.getStyle() + "-fx-background-color: " + COLOR_WOOD + "; -fx-text-fill: white;");
            } else {
                enableServerControls(false);
                showAlert(Alert.AlertType.INFORMATION, "Observer Mode", "Controls restricted. Use discovery mode only.");
            }
        });
        dlg.show();
    }

    // --- (Keep all your existing Logic/Reflection methods below) ---

    private void setupButtonActions() {
        btnStart.setOnAction(_ -> {
            if (!treeServerIsRunningViaReflection()) {
                Object existing = discoverServerInfoViaReflection(800);
                String existingIp = existing != null ? getDiscoveredServerIP(existing) : null;
                if (existing != null && existingIp != null && !treeServerIsLocalAddressViaReflection(existingIp)) {
                    showAlert(Alert.AlertType.WARNING, "Conflict Detected", "A registry is already broadcasting on this network.");
                    return;
                }

                startServerEngineViaReflection();
                PauseTransition wait = new PauseTransition(Duration.millis(500));
                wait.setOnFinished(_ -> {
                    refreshStatusLabel();
                    String actualHost = System.getProperty("java.rmi.server.hostname");
                    if (actualHost != null && !actualHost.isEmpty()) {
                        txtAdvertisedHost.setText(actualHost);
                    }
                });
                wait.play();
            }
        });

        btnStop.setOnAction(_ -> {
            if (treeServerIsRunningViaReflection()) {
                stopServerEngineViaReflection();
                refreshStatusLabel();
            }
        });
    }

    private void enableServerControls(boolean enable) {
        btnStart.setDisable(!enable);
        btnStop.setDisable(!enable);
        txtAdvertisedHost.setDisable(!enable);
    }

    private void handleContinue() {
        if (!treeServerIsRunningViaReflection()) {
            Object ds = discoverServerInfoViaReflection(1200);
            if (ds == null) {
                showAlert(Alert.AlertType.WARNING, "Connection Error", "No active registry found in the vicinity.");
                return;
            }
            saveClientConfigViaReflection(getDiscoveredServerIP(ds), getDiscoveredServerPort(ds));
            close();
            launchTreeClientViaReflection();
            return;
        }

        if ("admin".equals(adminUsername)) {
            try {
                java.rmi.registry.Registry registry = java.rmi.registry.LocateRegistry.getRegistry(treeServerGetPortViaReflection());
                new LoginGUI(registry).show();
                close();

                return;
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Dashboard Failure", ex.getMessage());
                return;
            }
        }
        close();
        launchTreeClientViaReflection();
    }

    private String getActualNetworkIP() throws UnknownHostException {
        try {
            return TreeServer.detectLocalNetworkIP();
        } catch (Exception e) {
            return InetAddress.getLocalHost().getHostAddress();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.getDialogPane().setStyle("-fx-background-color: " + COLOR_BG + ";");
        alert.showAndWait();
    }

    // --- Firewall & Network Configuration ---

    private void configureFirewall() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;
        }

        try {
            int rmiPort = treeServerGetPortViaReflection();

            String javaHome = System.getProperty("java.home", "");
            String javaExe = javaHome.isEmpty() ? ""
                    : javaHome + File.separator + "bin" + File.separator + "java.exe";

            File script = File.createTempFile("lorax_firewall_", ".bat");
            script.deleteOnExit();
            try (PrintWriter pw = new PrintWriter(script)) {
                pw.println("@echo off");
                pw.println("echo ============================================");
                pw.println("echo   The Lorax - Network Configuration");
                pw.println("echo ============================================");
                pw.println("echo.");

                // 1. Remove old rules first (netsh - works on all Windows versions)
                pw.println("echo [1/4] Cleaning old firewall rules...");
                pw.println("netsh advfirewall firewall delete rule name=\"Lorax RMI\" >nul 2>&1");
                pw.println("netsh advfirewall firewall delete rule name=\"Lorax RMI Ports\" >nul 2>&1");
                pw.println("netsh advfirewall firewall delete rule name=\"Lorax Discovery\" >nul 2>&1");
                pw.println("netsh advfirewall firewall delete rule name=\"Lorax Java\" >nul 2>&1");
                // Also clean the old PowerShell-style rules
                pw.println("powershell -NoProfile -Command \"Remove-NetFirewallRule -DisplayName 'Lorax RMI Registry' -ErrorAction SilentlyContinue\" >nul 2>&1");
                pw.println("powershell -NoProfile -Command \"Remove-NetFirewallRule -DisplayName 'Lorax TreeService' -ErrorAction SilentlyContinue\" >nul 2>&1");
                pw.println("powershell -NoProfile -Command \"Remove-NetFirewallRule -DisplayName 'Lorax UserService' -ErrorAction SilentlyContinue\" >nul 2>&1");
                pw.println("powershell -NoProfile -Command \"Remove-NetFirewallRule -DisplayName 'Lorax AdminService' -ErrorAction SilentlyContinue\" >nul 2>&1");
                pw.println("powershell -NoProfile -Command \"Remove-NetFirewallRule -DisplayName 'Lorax ChatService' -ErrorAction SilentlyContinue\" >nul 2>&1");
                pw.println("powershell -NoProfile -Command \"Remove-NetFirewallRule -DisplayName 'Lorax UDP Discovery' -ErrorAction SilentlyContinue\" >nul 2>&1");
                pw.println("powershell -NoProfile -Command \"Remove-NetFirewallRule -DisplayName 'Lorax Java Runtime' -ErrorAction SilentlyContinue\" >nul 2>&1");
                pw.println("echo    Done.");
                pw.println("echo.");

                // 2. Add firewall rules using netsh (single rule for all RMI ports)
                pw.println("echo [2/4] Opening RMI ports " + rmiPort + ",1100-1103 and UDP 50000...");
                pw.println("netsh advfirewall firewall add rule name=\"Lorax RMI Ports\" dir=in action=allow protocol=tcp localport=" + rmiPort + ",1100,1101,1102,1103 enable=yes profile=any");
                pw.println("netsh advfirewall firewall add rule name=\"Lorax Discovery\" dir=in action=allow protocol=udp localport=50000 enable=yes profile=any");
                pw.println("echo    Done.");
                pw.println("echo.");

                // 3. Allow the Java executable itself
                if (!javaExe.isEmpty()) {
                    pw.println("echo [3/4] Allowing Java runtime through firewall...");
                    pw.println("netsh advfirewall firewall add rule name=\"Lorax Java\" dir=in action=allow program=\"" + javaExe + "\" enable=yes profile=any");
                    pw.println("echo    Done.");
                } else {
                    pw.println("echo [3/4] Java path not detected, skipping program rule.");
                }
                pw.println("echo.");

                // 4. Switch all WiFi / active connections from Public to Private
                pw.println("echo [4/4] Setting network profile to Private (allows LAN discovery)...");
                pw.println("powershell -NoProfile -Command \"Get-NetConnectionProfile | Where-Object {$_.NetworkCategory -eq 'Public'} | Set-NetConnectionProfile -NetworkCategory Private\" 2>nul");
                pw.println("echo    Done.");
                pw.println("echo.");

                // Diagnostic output
                pw.println("echo ============================================");
                pw.println("echo   Verifying configuration...");
                pw.println("echo ============================================");
                pw.println("echo.");
                pw.println("echo Active firewall rules for Lorax:");
                pw.println("netsh advfirewall firewall show rule name=\"Lorax RMI Ports\" >nul 2>&1 && echo    [OK] Lorax RMI Ports || echo    [FAIL] Lorax RMI Ports");
                pw.println("netsh advfirewall firewall show rule name=\"Lorax Discovery\" >nul 2>&1 && echo    [OK] Lorax Discovery || echo    [FAIL] Lorax Discovery");
                pw.println("netsh advfirewall firewall show rule name=\"Lorax Java\" >nul 2>&1 && echo    [OK] Lorax Java || echo    [FAIL] Lorax Java");
                pw.println("echo.");
                pw.println("echo Network profile:");
                pw.println("powershell -NoProfile -Command \"Get-NetConnectionProfile | Select-Object Name, InterfaceAlias, NetworkCategory | Format-Table -AutoSize\"");
                pw.println("echo.");
                pw.println("echo Ports listening:");
                pw.println("netstat -an | findstr \":" + rmiPort + " :1100 :1101 :1102 :1103\"");
                pw.println("echo.");
                pw.println("echo ============================================");
                pw.println("echo   Configuration complete!");
                pw.println("echo   You can close this window.");
                pw.println("echo ============================================");
                pw.println("pause");
            }

            new ProcessBuilder(
                    "cmd", "/c",
                    "powershell -Command \"Start-Process cmd -Verb RunAs -ArgumentList '/c \\\"" +
                            script.getAbsolutePath().replace("\\", "\\\\") + "\\\"'\""
            ).start();

        } catch (Exception ex) {
            System.err.println("Firewall auto-config failed: " + ex.getMessage());
        }
    }

    // --- Reflection Helpers (Unchanged) ---
    private static void saveClientConfigViaReflection(String ip, int port) {
        try { Class.forName("client_GUI.ClientConfig").getMethod("saveConfig", String.class, int.class).invoke(null, ip, port); } catch (Exception _) {}
    }
    private static void launchTreeClientViaReflection() {
        try { Class.forName("client_GUI.TreeClient").getMethod("launchConnection").invoke(null); } catch (Exception _) {}
    }
    private static void showTreeDashboardViaReflection(java.rmi.registry.Registry reg, String user) {
        try {
            Object obj = Class.forName("client_GUI.TreeDashboard").getConstructor(java.rmi.registry.Registry.class, String.class).newInstance(reg, user);
            ((Stage) obj).show();
        } catch (Exception _) {}
    }
    private static boolean treeServerIsRunningViaReflection() {
        try { return (Boolean) Class.forName("Server.TreeServer").getMethod("isRunning").invoke(null); } catch (Exception e) { return false; }
    }
    private static Object discoverServerInfoViaReflection(int timeoutMs) {
        try { return Class.forName("Server.TreeServer").getMethod("discoverServerInfo", int.class).invoke(null, timeoutMs); } catch (Exception e) { return null; }
    }
    private static boolean treeServerIsLocalAddressViaReflection(String ip) {
        try { return (Boolean) Class.forName("Server.TreeServer").getMethod("isLocalAddress", String.class).invoke(null, ip); } catch (Exception e) { return false; }
    }
    private static void startServerEngineViaReflection() {
        try { Class.forName("Server.TreeServer").getMethod("startServerEngine").invoke(null); } catch (Exception _) {}
    }
    private static void stopServerEngineViaReflection() {
        try { Class.forName("Server.TreeServer").getMethod("stopServerEngine").invoke(null); } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    private static int treeServerGetPortViaReflection() {
        try { return ((Number) Class.forName("Server.TreeServer").getMethod("getPort").invoke(null)).intValue(); } catch (Exception e) { return 1099; }
    }
    private static String getDiscoveredServerIP(Object ds) {
        try { return (String) ds.getClass().getField("ip").get(ds); } catch (Exception e) { return null; }
    }
    private static int getDiscoveredServerPort(Object ds) {
        try { return ((Number) ds.getClass().getField("port").get(ds)).intValue(); } catch (Exception e) { return 0; }
    }
    private static void activateUserViaReflection(String u) {
        try { Class.forName("Server.SessionManager").getMethod("activateUser", String.class).invoke(null, u); } catch (Exception _) {}
    }
}
