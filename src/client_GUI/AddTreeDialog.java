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
                createLabel("Species"), fSpecies, errorLabel(fSpecies),
                createLabel("Height (m)"), fHeight, errorLabel(fHeight),
                createLabel("Diameter (cm)"), fDia, errorLabel(fDia),
                new HBox(12, new VBox(4, createLabel("Status"), fStatus), new VBox(4, createLabel("Planted date"), fDate, errorLabel(fDate))),
                createLabel("Field notes"), fNotes,
                new HBox(12, previewFrame, btnUpload)
        );

        // 2. RIGHT SIDE: Mapping & Actions
        VBox rightSide = new VBox(20);
        rightSide.setPadding(new Insets(30));
        rightSide.setAlignment(Pos.CENTER);

        Label lblMapTitle = new Label("Planting location");
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
        fLoc.textProperty().addListener((o, a, v) -> { if (fieldErrors.containsKey(fLoc) && fieldErrors.get(fLoc).isVisible()) validate(false); });
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

        rightSide.getChildren().addAll(lblMapTitle, lblMapWarning, mapPanel, createLabel("Coordinates"), fLoc, errorLabel(fLoc), actions);

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
        styleField(f, false, false);
        f.focusedProperty().addListener((_, _, focused) -> {
            if (!focused && !f.getText().isEmpty()) validate(false); // check a field as soon as the user leaves it
            styleField(f, focused, errorFor(f) != null && errorFor(f).isVisible());
        });
        return f;
    }

    private void styleField(TextField f, boolean focused, boolean invalid) {
        String line = invalid ? "#9D2235" : focused ? COLOR_LEAF : "#CCC";
        f.setStyle("-fx-background-color: transparent; -fx-border-color: transparent transparent " + line + " transparent;"
                + " -fx-border-width: 0 0 " + (invalid || focused ? 2 : 1) + " 0; -fx-padding: 5 0 5 0;");
    }

    // ---- inline validation: one message under each field instead of pop-up dialogs
    private final java.util.Map<TextField, Label> fieldErrors = new java.util.HashMap<>();

    private Label errorLabel(TextField f) {
        Label l = new Label();
        l.setStyle("-fx-text-fill: #9D2235; -fx-font-size: 11.5px;");
        l.setWrapText(true);
        l.setVisible(false);
        l.setManaged(false);
        fieldErrors.put(f, l);
        return l;
    }

    private Label errorFor(TextField f) {
        return fieldErrors.get(f);
    }

    private boolean setError(TextField f, String message) {
        Label l = fieldErrors.get(f);
        boolean bad = message != null;
        if (l != null) {
            l.setText(bad ? message : "");
            l.setVisible(bad);
            l.setManaged(bad);
        }
        styleField(f, f.isFocused(), bad);
        return !bad;
    }

    /**
     * Checks every field and shows a message under each problem. With {@code all=false} (on leaving a field) empty
     * fields are not flagged yet, so the user isn't scolded before they had a chance to type.
     */
    private boolean validate(boolean all) {
        boolean ok = true;
        String sp = fSpecies.getText().trim();
        ok &= setError(fSpecies, sp.isEmpty() ? (all ? "Enter the species (common or scientific name)." : null)
                : sp.length() > 80 ? "Keep the species name under 80 characters." : null);
        String loc = fLoc.getText().trim();
        boolean hasLatLon = GeoCoordFormat.tryParse(loc) != null;
        boolean needPin = loc.isEmpty() || loc.toLowerCase(Locale.ROOT).contains("select from map") || (treeToEdit == null && !hasLatLon);
        ok &= setError(fLoc, needPin ? (all ? "Click the map to pin where the tree was planted." : null) : null);
        ok &= setError(fHeight, numberProblem(fHeight.getText(), "Height", 0.05, 120, "m", all));
        ok &= setError(fDia, numberProblem(fDia.getText(), "Diameter", 0.1, 1500, "cm", all));
        String date = fDate.getText().trim();
        String dateErr = null;
        if (date.isEmpty()) dateErr = all ? "Enter the planting date (YYYY-MM-DD)." : null;
        else {
            try {
                LocalDate d = LocalDate.parse(date);
                if (d.isAfter(LocalDate.now())) dateErr = "The planting date can't be in the future.";
                else if (d.getYear() < 1800) dateErr = "Check the year of the planting date.";
            } catch (java.time.DateTimeException ex) {
                dateErr = "Use the format YYYY-MM-DD, for example " + LocalDate.now() + ".";
            }
        }
        ok &= setError(fDate, dateErr);
        return ok;
    }

    private static String numberProblem(String raw, String name, double min, double max, String unit, boolean all) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) return all ? "Enter the " + name.toLowerCase(Locale.ROOT) + " in " + unit + "." : null;
        try {
            double v = Double.parseDouble(t);
            if (!Double.isFinite(v) || v <= 0) return name + " must be a positive number.";
            if (v < min || v > max) return name + " should be between " + min + " and " + (int) max + " " + unit + ".";
            return null;
        } catch (NumberFormatException e) {
            return name + " must be a number, for example 2.5.";
        }
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
        if (!validate(true)) {
            // Focus the first problem so the user can fix it straight away.
            for (TextField f : java.util.List.of(fSpecies, fHeight, fDia, fDate)) {
                Label l = fieldErrors.get(f);
                if (l != null && l.isVisible()) { f.requestFocus(); break; }
            }
            return;
        }
        String loc = fLoc.getText().trim();
        double h = Double.parseDouble(fHeight.getText().trim());
        double d = Double.parseDouble(fDia.getText().trim());
        resultTree = new Tree(
                (treeToEdit != null) ? treeToEdit.id : 0,
                fSpecies.getText().trim(), loc, h, d,
                fDate.getText().trim(), fStatus.getValue(), fNotes.getText(),
                imageDirty ? currentImageBytes : (treeToEdit != null ? treeToEdit.imageBytes : null),
                ownerUsername
        );
        if (!imageDirty) resultTree.imagePath = existingImagePath;
        close();
    }

    public Tree getTree() { return resultTree; }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
