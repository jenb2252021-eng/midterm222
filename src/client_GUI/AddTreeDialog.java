package client_GUI;

import Model.Tree;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Locale;

public class AddTreeDialog extends Stage {

    private final String COLOR_WOOD    = "#17251b"; // Bark Brown
    private final String COLOR_LEAF    = "#20865b"; // Forest Green
    private final String COLOR_MOSS    = "#576b5d"; // Sage Accent
    private final TextField fSpecies;
    private final TextField fLoc;
    private final TextField fHeight;
    private final TextField fDia;
    private final TextField fDate;
    private final ComboBox<String> fStatus;
    private final TextArea fNotes;
    private final ImageView lblImagePreview;
    private final Label lblImageText;

    private byte[] currentImageBytes = null;
    private boolean imageDirty = false;
    private String existingImagePath = null;
    private Tree resultTree = null;
    private final Tree treeToEdit;
    private final String ownerUsername;

    public AddTreeDialog(Window parent, Tree treeToEdit, String ownerUsername) {
        initOwner(parent);
        initModality(Modality.WINDOW_MODAL);
        setTitle(treeToEdit == null ? "Forest Log - New Entry" : "Forest Log - Update Entry");

        this.treeToEdit = treeToEdit;
        boolean isEditMode = (treeToEdit != null);
        this.ownerUsername = isEditMode ? treeToEdit.ownerUsername : ownerUsername;

        if (isEditMode) {
            this.currentImageBytes = treeToEdit.imageBytes;
            this.existingImagePath = treeToEdit.imagePath;
        }

        // --- UI Construction ---
        HBox root = new HBox(0);

        // Light Moss
        String COLOR_BG = "#f5f7f5";
        root.setStyle("-fx-background-color: " + COLOR_BG + ";");

        // 1. LEFT SIDE: Data Entry (The Parchment Journal)
        VBox leftSide = new VBox(9);
        leftSide.setPadding(new Insets(20));
        leftSide.setPrefWidth(380);
        // Parchment
        String COLOR_PAPER = "#ffffff";
        leftSide.setStyle("-fx-background-color: " + COLOR_PAPER + "; -fx-background-radius: 12;");

        Label lblHeader = new Label(isEditMode ? "Edit record" : "Register Sapling");
        lblHeader.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        lblHeader.setTextFill(Color.valueOf(COLOR_WOOD));

        fSpecies = createField("Scientific or Common Name");
        fLoc = createField("Select from map...");
        fLoc.setEditable(false);
        fHeight = createField("Height in meters");
        fDia = createField("Diameter in centimeters");
        fDate = createField("");
        fDate.setText(LocalDate.now().toString());

        fStatus = new ComboBox<>(FXCollections.observableArrayList("Healthy", "Monitoring", "At Risk"));
        if (isEditMode && treeToEdit.status != null && treeToEdit.status.equalsIgnoreCase("Destroyed")) {
            fStatus.getItems().add("Destroyed");
        }
        fStatus.setValue("Healthy");
        fStatus.setMaxWidth(Double.MAX_VALUE);
        fStatus.setStyle("-fx-background-color: white; -fx-border-color: " + COLOR_BG + ";");

        fNotes = new TextArea();
        fNotes.setPrefRowCount(3);
        fNotes.setPromptText("Botanical observations...");
        fNotes.setStyle("-fx-control-inner-background: white; -fx-border-color: " + COLOR_BG + ";");

        // Photo Preview Area
        StackPane previewFrame = new StackPane();
        previewFrame.setPrefSize(60, 60);
        previewFrame.setStyle("-fx-border-color: " + COLOR_MOSS + "; -fx-border-style: dashed; -fx-border-radius: 10;");

        lblImagePreview = new ImageView();
        lblImagePreview.setFitWidth(60);
        lblImagePreview.setFitHeight(60);
        lblImagePreview.setPreserveRatio(true);
        lblImageText = new Label("No Photo");
        lblImageText.setTextFill(Color.GRAY);
        previewFrame.getChildren().addAll(lblImageText, lblImagePreview);

        Button btnUpload = new Button("Add field photo");
        styleSecondaryButton(btnUpload);
        btnUpload.setOnAction(_ -> chooseImage());

        leftSide.getChildren().addAll(
                RegistryStyle.caption("NEW FIELD RECORD"), lblHeader,
                createLabel("Species"), fSpecies,
                createLabel("Height (m)"), fHeight,
                createLabel("Diameter (cm)"), fDia,
                new HBox(12, new VBox(4, createLabel("Status"), fStatus), new VBox(4, createLabel("Planted date"), fDate)),
                createLabel("Field notes"), fNotes,
                new HBox(12, previewFrame, btnUpload)
        );

        // 2. RIGHT SIDE: Mapping & Actions
        VBox rightSide = new VBox(20);
        rightSide.setPadding(new Insets(30));
        rightSide.setAlignment(Pos.CENTER);

        Label lblMapTitle = new Label("Field Map");
        lblMapTitle.setFont(Font.font("System", FontWeight.BOLD, 12));
        lblMapTitle.setTextFill(Color.valueOf(COLOR_LEAF));

        Label lblMapWarning = new Label(
                "Click the map to place the sapling precisely.");
        lblMapWarning.setWrapText(true);
        lblMapWarning.setMaxWidth(Double.MAX_VALUE);
        lblMapWarning.maxWidthProperty().bind(rightSide.widthProperty().subtract(40));
        lblMapWarning.setFont(Font.font("System", 11));
        lblMapWarning.setTextFill(Color.valueOf("#8B4513"));

        TreeMapPickerPane mapPanel = new TreeMapPickerPane(fLoc);
        applyShadow(mapPanel);
        HBox.setHgrow(rightSide, Priority.ALWAYS);
        VBox.setVgrow(mapPanel, Priority.ALWAYS);
        rightSide.setMinWidth(420);

        // Action Buttons
        HBox actions = new HBox(15);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button btnSave = new Button(isEditMode ? "Save Changes" : "Log to Forest");
        stylePrimaryButton(btnSave);
        btnSave.setOnAction(_ -> performSave());

        Button btnCancel = new Button("Cancel");
        styleSecondaryButton(btnCancel);
        btnCancel.setOnAction(_ -> close());

        actions.getChildren().addAll(btnCancel, btnSave);

        rightSide.getChildren().addAll(lblMapTitle, lblMapWarning, mapPanel, createLabel("Coordinates"), fLoc, actions);

        root.getChildren().addAll(leftSide, rightSide);

        Scene scene = RegistryStyle.scene(root, 900, 660);
        setScene(scene);
        root.prefWidthProperty().bind(scene.widthProperty());
        root.prefHeightProperty().bind(scene.heightProperty());

        setOnShowing(_ -> {
            mapPanel.syncPinFromCoordinates();
            mapPanel.refreshMapLayout();
        });
        setOnHidden(_ -> mapPanel.dispose());

        if (isEditMode) populateFromTree(treeToEdit);
    }

    // --- Helper Styling Methods ---
    private TextField createField(String prompt) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        f.setStyle("-fx-background-color: transparent; -fx-border-color: transparent transparent #CCC transparent; -fx-padding: 5 0 5 0;");
        f.focusedProperty().addListener((_, _, newVal) ->
                f.setStyle("-fx-background-color: transparent; -fx-border-color: transparent transparent " + (newVal ? COLOR_LEAF : "#CCC") + " transparent;"));
        return f;
    }

    private Label createLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 10));
        l.setTextFill(Color.valueOf(COLOR_MOSS));
        return l;
    }

    private void stylePrimaryButton(Button b) {
        b.setStyle("-fx-background-color: " + COLOR_LEAF + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10 25; -fx-cursor: hand;");
    }

    private void styleSecondaryButton(Button b) {
        b.setStyle("-fx-background-color: transparent; -fx-border-color: " + COLOR_LEAF + "; -fx-border-radius: 10; -fx-text-fill: " + COLOR_LEAF + "; -fx-cursor: hand;");
    }

    private void applyShadow(javafx.scene.Node n) {
        DropShadow ds = new DropShadow();
        ds.setColor(Color.web("#000000", 0.15));
        ds.setRadius(15);
        ds.setOffsetY(5);
        n.setEffect(ds);
    }

    // --- Original Logic (Meticulously Restored) ---

    private void populateFromTree(Tree t) {
        fSpecies.setText(t.species);
        fLoc.setText(t.location);
        fHeight.setText(String.valueOf(t.height));
        fDia.setText(String.valueOf(t.diameter));
        fDate.setText(t.plantedDate);
        fStatus.setValue(t.status);
        fNotes.setText(t.notes);
        updateImageDisplay();
    }

    private void updateImageDisplay() {
        if (currentImageBytes != null && currentImageBytes.length > 0) {
            Image img = new Image(new ByteArrayInputStream(currentImageBytes));
            if (!img.isError()) {
                lblImagePreview.setImage(img);
                lblImageText.setVisible(false);
                return;
            }
        }
        lblImagePreview.setImage(null);
        lblImageText.setVisible(true);
    }

    private void chooseImage() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Field photos", "*.jpg", "*.jpeg", "*.png"));
        File file = chooser.showOpenDialog(this);
        if (file != null) {
            try {
                if (Files.size(file.toPath()) > 20L * 1024 * 1024) throw new IllegalArgumentException();
                byte[] photo = Files.readAllBytes(file.toPath());
                if (new Image(new ByteArrayInputStream(photo)).isError()) throw new IllegalArgumentException();
                currentImageBytes = photo;
                imageDirty = true;
                updateImageDisplay();
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Image Error", "Failed to load image.");
            }
        }
    }

    private void performSave() {
        try {
            String loc = fLoc.getText().trim();
            String locLower = loc.toLowerCase(Locale.ROOT);
            if (fSpecies.getText().trim().isEmpty() || loc.isEmpty() || locLower.contains("select from map")) {
                showAlert(Alert.AlertType.WARNING, "Survey Incomplete", "Species and a pinned map location are required.");
                return;
            }
            boolean hasLatLon = GeoCoordFormat.tryParse(loc) != null;
            if (treeToEdit == null && !hasLatLon) {
                showAlert(Alert.AlertType.WARNING, "Survey Incomplete",
                        "Pin a location on the map so coordinates are saved as latitude, longitude.");
                return;
            }
            double h = Double.parseDouble(fHeight.getText());
            double d = Double.parseDouble(fDia.getText());

            if (!Double.isFinite(h) || !Double.isFinite(d) || h <= 0 || d <= 0) {
                showAlert(Alert.AlertType.WARNING, "Check measurements", "Height and diameter must be positive numbers.");
                return;
            }
            try { LocalDate.parse(fDate.getText().trim()); }
            catch (java.time.DateTimeException ex) {
                showAlert(Alert.AlertType.WARNING, "Planted date", "Use a valid date in YYYY-MM-DD format.");
                return;
            }
            resultTree = new Tree(
                    (treeToEdit != null) ? treeToEdit.id : 0,
                    fSpecies.getText(), loc, h, d,
                    fDate.getText().trim(), fStatus.getValue(), fNotes.getText(),
                    imageDirty ? currentImageBytes : (treeToEdit != null ? treeToEdit.imageBytes : null),
                    ownerUsername
            );
            if (!imageDirty) resultTree.imagePath = existingImagePath;
            close();
        } catch (NumberFormatException ex) {
            showAlert(Alert.AlertType.ERROR, "Invalid Data", "Check numeric values for height and diameter.");
        }
    }

    public Tree getTree() { return resultTree; }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
