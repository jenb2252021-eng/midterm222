package client_GUI;

import Server.TreeServer;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.rmi.registry.LocateRegistry;

public class ClientSetupDialog extends Stage {
    // --- Earthified Botanical Palette ---
    private final String COLOR_BG      = "#F1F2DA"; // Light Moss
    private final String COLOR_PAPER   = "#FCFBF4"; // Parchment
    private final String COLOR_WOOD    = "#3E2723"; // Bark Brown
    private final String COLOR_LEAF    = "#4F772D"; // Forest Green
    private final String COLOR_MOSS    = "#90A955"; // Sage Accent
    private final String COLOR_ACCENT  = "#E0DDCF";
    private final String COLOR_WARN    = "#C88200"; // Warning Orange

    private TextField txtServerIP;
    private TextField txtRMIPort;
    private Label lblStatus;

    public ClientSetupDialog() {
        setTitle("Ranger Network - Connection Setup");
        setWidth(520);
        setHeight(560);
        setResizable(true);
        setMinWidth(420);
        setMinHeight(480);

        System.setProperty("sun.rmi.transport.connectionTimeout", "5000");
        System.setProperty("sun.rmi.transport.tcp.responseTimeout", "5000");

        initUI();
        centerOnScreen();

        // Auto-detect on startup
        Platform.runLater(this::autoDetectAndPrefill);

        setOnCloseRequest(_ -> {
            Platform.exit();
            System.exit(0);
        });
    }

    private void initUI() {
        VBox root = new VBox(25);
        root.setPadding(new Insets(35));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: " + COLOR_BG + ";");

        // 1. Header Section
        VBox header = new VBox(5);
        header.setAlignment(Pos.CENTER);

        Label title = new Label("THE LORAX");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 32));
        title.setTextFill(Color.valueOf(COLOR_WOOD));

        Label subtitle = new Label("ESTABLISHING RANGER NETWORK LINK");
        subtitle.setFont(Font.font("System", FontWeight.BOLD, 12));
        subtitle.setTextFill(Color.valueOf(COLOR_LEAF));
        subtitle.setLineSpacing(2.0);

        header.getChildren().addAll(title, subtitle);

        // 2. Configuration Card (Parchment Style)
        VBox card = new VBox(15);
        card.setPadding(new Insets(30));
        card.setStyle(
                "-fx-background-color: " + COLOR_PAPER + ";" +
                        "-fx-background-radius: 15 40 15 40;" + // Asymmetric "leaf" corners
                        "-fx-border-color: " + COLOR_ACCENT + ";" +
                        "-fx-border-radius: 15 40 15 40;" +
                        "-fx-border-width: 1;"
        );

        // Soft drop shadow for depth
        DropShadow ds = new DropShadow();
        ds.setColor(Color.web("#000000", 0.12));
        ds.setRadius(15);
        ds.setOffsetY(5);
        card.setEffect(ds);

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER);

        txtServerIP = new TextField();
        styleInputField(txtServerIP, "0.0.0.0");

        txtRMIPort = new TextField("1099");
        styleInputField(txtRMIPort, "Port");

        grid.add(createFieldLabel("SERVER IP"), 0, 0);
        grid.add(txtServerIP, 1, 0);
        grid.add(createFieldLabel("RMI PORT"), 0, 1);
        grid.add(txtRMIPort, 1, 1);

        // Status Indicator Box
        HBox statusBox = new HBox(10);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setPadding(new Insets(10, 0, 0, 0));

        lblStatus = new Label("Initializing Sensor Scan...");
        lblStatus.setFont(Font.font("System", FontWeight.THIN, 13));
        lblStatus.setTextFill(Color.valueOf(COLOR_WOOD));

        statusBox.getChildren().add(lblStatus);
        card.getChildren().addAll(grid, statusBox);

        // 3. Action Buttons
        HBox buttonRow = new HBox(12);
        buttonRow.setAlignment(Pos.CENTER);

        Button btnAuto = new Button("Auto-Scan");
        styleSecondaryButton(btnAuto);
        btnAuto.setOnAction(_ -> autoDetectAndPrefill());

        Button btnTest = new Button("Verify Link");
        styleSecondaryButton(btnTest);
        btnTest.setOnAction(_ -> testConnection());

        Button btnSave = new Button("CONNECT TO FOREST");
        stylePrimaryButton(btnSave);
        btnSave.setOnAction(_ -> saveAndContinue());

        Button btnDiag = new Button("Diagnose");
        styleSecondaryButton(btnDiag);
        btnDiag.setOnAction(_ -> runDiagnostics());

        buttonRow.getChildren().addAll(btnAuto, btnTest, btnDiag, btnSave);

        root.getChildren().addAll(header, card, buttonRow);
        setScene(new Scene(root));
    }

    private void styleInputField(TextField f, String prompt) {
        f.setPromptText(prompt);
        f.setPrefHeight(38);
        f.setPrefWidth(200);
        f.setStyle(
                "-fx-background-color: white;" +
                        "-fx-border-color: transparent transparent " + COLOR_MOSS + " transparent;" +
                        "-fx-border-width: 0 0 2 0;" +
                        "-fx-padding: 5;"
        );
    }

    private Label createFieldLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 10));
        l.setTextFill(Color.valueOf(COLOR_MOSS));
        return l;
    }

    private void stylePrimaryButton(Button b) {
        b.setStyle(
                "-fx-background-color: " + COLOR_LEAF + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 10;" +
                        "-fx-padding: 10 20;" +
                        "-fx-cursor: hand;"
        );
    }

    private void styleSecondaryButton(Button b) {
        b.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-border-color: " + COLOR_WOOD + ";" +
                        "-fx-border-radius: 10;" +
                        "-fx-text-fill: " + COLOR_WOOD + ";" +
                        "-fx-padding: 10 20;" +
                        "-fx-cursor: hand;"
        );
    }

    // --- Logic Methods (Restored and Refined) ---

    private void autoDetectAndPrefill() {
        lblStatus.setText("📡 Scanning LAN for Ranger HQ...");
        lblStatus.setTextFill(Color.valueOf(COLOR_WARN));

        new Thread(() -> {
            try {
                TreeServer.DiscoveredServer ds = TreeServer.discoverServerInfo(1500);
                Platform.runLater(() -> {
                    if (ds == null) {
                        lblStatus.setText("❌ No HQ detected. Enter IP manually.");
                        lblStatus.setTextFill(Color.RED);
                    } else {
                        txtServerIP.setText(ds.ip);
                        txtRMIPort.setText(String.valueOf(ds.port));
                        lblStatus.setText("✅ HQ Located at " + ds.ip);
                        lblStatus.setTextFill(Color.valueOf(COLOR_LEAF));
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    lblStatus.setText("⚠️ Discovery Error.");
                    lblStatus.setTextFill(Color.RED);
                });
            }
        }).start();
    }

    private void testConnection() {
        String ip = txtServerIP.getText().trim();
        String portStr = txtRMIPort.getText().trim();
        if (ip.isEmpty() || portStr.isEmpty()) return;

        lblStatus.setText("⌛ Verifying link integrity...");
        lblStatus.setTextFill(Color.valueOf(COLOR_WARN));

        new Thread(() -> {
            try {
                int port = Integer.parseInt(portStr);
                var registry = LocateRegistry.getRegistry(ip, port);
                registry.lookup("UserService");
                Platform.runLater(() -> {
                    lblStatus.setText("✨ Link Verified! Ready to sync.");
                    lblStatus.setTextFill(Color.valueOf(COLOR_LEAF));
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    lblStatus.setText("❌ Verification Failed: " + ex.getClass().getSimpleName());
                    lblStatus.setTextFill(Color.RED);
                });
            }
        }).start();
    }

    private void saveAndContinue() {
        String ip = txtServerIP.getText().trim();
        String portStr = txtRMIPort.getText().trim();

        if (ip.isEmpty() || portStr.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Missing Info", "Coordinate and Port data required.");
            return;
        }

        try {
            int port = Integer.parseInt(portStr);
            lblStatus.setText("Finalizing sync...");

            new Thread(() -> {
                try {
                    var registry = LocateRegistry.getRegistry(ip, port);
                    registry.lookup("UserService");
                    Platform.runLater(() -> {
                        ClientConfig.saveConfig(ip, port);
                        setOnCloseRequest(null);
                        close();
                        TreeClient.launchConnection();
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Sync Error",
                            "Could not establish persistent link.\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage()));
                }
            }).start();
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Input Error", "Invalid port number.");
        }
    }

    private void runDiagnostics() {
        String ip = txtServerIP.getText().trim();
        String portStr = txtRMIPort.getText().trim();
        if (ip.isEmpty()) {
            lblStatus.setText("❌ Enter a Server IP first.");
            lblStatus.setTextFill(Color.RED);
            return;
        }

        int port;
        try { port = Integer.parseInt(portStr); } catch (Exception e) { port = 1099; }
        final int rmiPort = port;

        lblStatus.setText("🔍 Running network diagnostics...");
        lblStatus.setTextFill(Color.valueOf(COLOR_WARN));

        new Thread(() -> {
            StringBuilder report = new StringBuilder();
            report.append("=== Network Diagnostics for ").append(ip).append(" ===\n\n");

            // Test 1: Ping (uses OS command, not Java sockets)
            report.append("1. PING TEST:\n");
            try {
                String cmd = System.getProperty("os.name", "").toLowerCase().contains("win")
                        ? "ping -n 2 -w 2000 " + ip
                        : "ping -c 2 -W 2 " + ip;
                Process proc = Runtime.getRuntime().exec(cmd);
                BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    report.append("   ").append(line).append("\n");
                }
                int exitCode = proc.waitFor();
                report.append("   Result: ").append(exitCode == 0 ? "REACHABLE" : "UNREACHABLE").append("\n");
            } catch (Exception e) {
                report.append("   Error: ").append(e.getMessage()).append("\n");
            }

            // Test 2: RMI Registry lookup
            report.append("\n2. RMI REGISTRY TEST (port ").append(rmiPort).append("):\n");
            try {
                var registry = LocateRegistry.getRegistry(ip, rmiPort);
                String[] services = registry.list();
                report.append("   CONNECTED! Services found: ").append(services.length).append("\n");
                for (String s : services) {
                    report.append("   - ").append(s).append("\n");
                }
            } catch (Exception e) {
                report.append("   FAILED: ").append(e.getClass().getSimpleName()).append("\n");
                report.append("   ").append(e.getMessage()).append("\n");
                if (e.getCause() != null) {
                    report.append("   Cause: ").append(e.getCause().getMessage()).append("\n");
                }
            }

            // Test 3: UDP Discovery
            report.append("\n3. UDP DISCOVERY TEST:\n");
            try {
                TreeServer.DiscoveredServer ds = TreeServer.discoverServerInfo(2000);
                if (ds != null) {
                    report.append("   FOUND server at ").append(ds.ip).append(":").append(ds.port).append("\n");
                } else {
                    report.append("   No server responded to UDP broadcast\n");
                }
            } catch (Exception e) {
                report.append("   Error: ").append(e.getMessage()).append("\n");
            }

            String finalReport = report.toString();
            System.out.println(finalReport);

            Platform.runLater(() -> {
                Alert diag = new Alert(Alert.AlertType.INFORMATION);
                diag.setTitle("Network Diagnostics");
                diag.setHeaderText("Connection report for " + ip);

                TextArea ta = new TextArea(finalReport);
                ta.setEditable(false);
                ta.setWrapText(true);
                ta.setFont(Font.font("Consolas", 11));
                ta.setPrefHeight(350);
                ta.setPrefWidth(500);
                diag.getDialogPane().setContent(ta);
                diag.getDialogPane().setPrefWidth(550);
                diag.getDialogPane().setStyle("-fx-background-color: " + COLOR_PAPER + ";");
                diag.showAndWait();

                if (finalReport.contains("UNREACHABLE")) {
                    lblStatus.setText("❌ Server not reachable. Check network/WiFi.");
                    lblStatus.setTextFill(Color.RED);
                } else if (finalReport.contains("CONNECTED!")) {
                    lblStatus.setText("✅ All tests passed!");
                    lblStatus.setTextFill(Color.valueOf(COLOR_LEAF));
                } else {
                    lblStatus.setText("⚠️ Ping OK but RMI blocked. Server firewall issue.");
                    lblStatus.setTextFill(Color.valueOf(COLOR_WARN));
                }
            });
        }, "NetworkDiagnostics").start();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.getDialogPane().setStyle("-fx-background-color: " + COLOR_PAPER + ";");
        alert.showAndWait();
    }
}