package client_GUI;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;

final class RegistryStyle {
    static Scene scene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(RegistryStyle.class.getResource("/res/registry.css").toExternalForm());
        return scene;
    }
    static StackPane leaf() {
        SVGPath icon = new SVGPath();
        icon.setContent("M20 3 C8 2 2 9 5 16 C8 22 19 19 20 3 Z M5 20 L15 9");
        icon.setStyle("-fx-fill: transparent; -fx-stroke: white; -fx-stroke-width: 1.5;");
        StackPane box = new StackPane(icon);
        box.setMinSize(32,32); box.setPrefSize(32,32); box.setMaxSize(32,32);
        box.getStyleClass().add("leaf");
        return box;
    }
    static SVGPath navIcon(String name) {
        String outline = switch (name) {
            case "Registry" -> "M2 2h6v6H2z M12 2h6v6h-6z M2 12h6v6H2z M12 12h6v6h-6z";
            case "Field Map" -> "M2 5l5-3 6 3 5-3v13l-5 3-6-3-5 3z M7 2v13 M13 5v13";
            case "Messages" -> "M3 3h14v10H7l-4 4z";
            case "Profile", "Manage Users" -> "M13 6a3 3 0 1 1-6 0a3 3 0 1 1 6 0 M3 17c0-7 14-7 14 0";
            case "Archive" -> "M2 3h16v4H2z M4 7v10h12V7 M8 10h4";
            case "Ranger Access" -> "M10 2l7 3v5c0 4-4 7-7 8-3-1-7-4-7-8V5z";
            case "System States" -> "M1 10h4l3-7 4 14 3-7h4";
            case "Sign Out" -> "M9 2H3v16h6 M8 10h10 M14 6l4 4-4 4";
            default -> "M4 3h12v15H4z M7 7h6 M7 11h5";
        };
        SVGPath icon = new SVGPath();
        icon.setContent(outline);
        icon.setStyle("-fx-fill: transparent; -fx-stroke: #527261; -fx-stroke-width: 1.4; -fx-stroke-linejoin: round; -fx-stroke-linecap: round;");
        return icon;
    }
    static Region spacer() {
        Region space = new Region(); HBox.setHgrow(space, Priority.ALWAYS); return space;
    }
    static Label heading(String text) {
        Label l = new Label(text); l.getStyleClass().add("heading"); return l;
    }
    static Label caption(String text) {
        Label l = new Label(text); l.getStyleClass().add("caption"); return l;
    }
    static HBox brand(boolean registry) {
        VBox name = new VBox(2, new Label("The Lorax"));
        if (registry) name.getChildren().add(caption("Baguio Registry"));
        HBox row = new HBox(10, leaf(), name);
        row.setAlignment(Pos.CENTER_LEFT); return row;
    }
    static Button primary(String name, Runnable action) {
        Button b = new Button(name); b.getStyleClass().add("primary");
        b.setOnAction(e -> action.run()); return b;
    }
}
