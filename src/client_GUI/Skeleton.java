package client_GUI;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/** Loading skeletons and empty/error states shared by every page, so all screens behave the same way. */
final class Skeleton {
    private Skeleton() {}

    /** Gentle pulse while content loads; stops when the node leaves the screen. */
    static void pulse(Node n) {
        FadeTransition f = new FadeTransition(Duration.millis(750), n);
        f.setFromValue(1);
        f.setToValue(0.45);
        f.setAutoReverse(true);
        f.setCycleCount(Animation.INDEFINITE);
        f.play();
        n.sceneProperty().addListener((o, a, s) -> { if (s == null) f.stop(); });
    }

    /** Placeholder rows shaped like a table while data loads. */
    static Node rows(int count) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        for (int i = 0; i < count; i++) {
            Region a = bar(34, 34), b = bar(180 + (i * 37) % 90, 14), c = bar(90, 14), d = bar(120, 14);
            HBox row = new HBox(16, a, new VBox(6, b, bar(110, 10)), c, d);
            row.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().add(row);
        }
        pulse(box);
        return box;
    }

    static Region bar(double w, double h) {
        Region r = new Region();
        r.getStyleClass().add("skeleton");
        r.setMinSize(w, h);
        r.setPrefSize(w, h);
        r.setMaxSize(w, h);
        return r;
    }

    /** Friendly empty state with an icon, a title, a hint and an optional action. */
    static Node empty(String iconPath, String title, String hint, Button action) {
        SVGPath icon = new SVGPath();
        icon.setContent(iconPath);
        icon.setStyle("-fx-fill: #eef5f0; -fx-stroke: #20865b; -fx-stroke-width: 1.8; -fx-stroke-line-join: round; -fx-stroke-line-cap: round;");
        icon.setScaleX(1.8);
        icon.setScaleY(1.8);
        Label t = new Label(title);
        t.getStyleClass().add("section-title");
        Label h = new Label(hint);
        h.getStyleClass().add("muted");
        h.setWrapText(true);
        h.setMaxWidth(420);
        h.setStyle("-fx-text-alignment: center;");
        VBox box = new VBox(12, icon, t, h);
        if (action != null) box.getChildren().add(action);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(48, 24, 48, 24));
        box.getStyleClass().add("state-box");
        return box;
    }

    /** Error state with a retry button. */
    static Node error(String message, Runnable retry) {
        Button again = RegistryStyle.secondary("Try again", retry);
        return empty("M12 3l10 18H2z M12 10v5 M12 18v.5", "Something went wrong", message, again);
    }

    static final String ICON_INBOX = "M3 13l3-9h12l3 9v7H3z M3 13h5l1 3h6l1-3h5";
    static final String ICON_ARCHIVE = "M3 4h18v5H3z M5 9v11h14V9 M10 13h4";
    static final String ICON_USERS = "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M2 21c0-4 3-7 7-7s7 3 7 7 M16 3.5a4 4 0 0 1 0 7.5 M18 14c2.5.8 4 3.5 4 7";
    static final String ICON_LOG = "M4 4h16v16H4z M8 9h8 M8 13h8 M8 17h5";
}
