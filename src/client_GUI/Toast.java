package client_GUI;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Small, non-blocking notifications in the bottom-right corner of the app window (success, info, error).
 * They replace "OK" dialogs for routine confirmations, so the user is never interrupted for good news.
 */
public final class Toast {

    public enum Kind { SUCCESS, INFO, ERROR }

    private static final List<Popup> OPEN = new ArrayList<>();

    private Toast() {}

    public static void success(String message) { show(null, message, Kind.SUCCESS); }
    public static void info(String message) { show(null, message, Kind.INFO); }
    public static void error(String message) { show(null, message, Kind.ERROR); }

    /** Shows a toast over {@code owner}, or over the focused app window when owner is null. */
    public static void show(Window owner, String message, Kind kind) {
        Window w = owner != null ? owner : activeWindow();
        if (w == null || message == null || message.isBlank()) return;
        String accent = switch (kind) { case SUCCESS -> "#20865B"; case ERROR -> "#9D2235"; default -> "#457B9D"; };
        SVGPath icon = new SVGPath();
        icon.setContent(switch (kind) {
            case SUCCESS -> "M3 9l4 4 8-9";
            case ERROR -> "M9 4v6 M9 13v1";
            default -> "M9 8v6 M9 4v1";
        });
        icon.setStyle("-fx-fill: transparent; -fx-stroke: white; -fx-stroke-width: 2; -fx-stroke-line-cap: round;");
        StackPane badge = new StackPane(icon);
        badge.setMinSize(24, 24);
        badge.setMaxSize(24, 24);
        badge.setStyle("-fx-background-color: " + accent + "; -fx-background-radius: 999;");
        Label text = new Label(clean(message));
        text.setWrapText(true);
        text.setMaxWidth(340);
        text.setStyle("-fx-text-fill: #18231e; -fx-font-size: 13px; -fx-font-family: 'Segoe UI', 'SF Pro Text', sans-serif;");
        HBox box = new HBox(12, badge, text);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 12 16;"
                + "-fx-border-color: #dfe5e0 #dfe5e0 #dfe5e0 " + accent + "; -fx-border-width: 1 1 1 4; -fx-border-radius: 12;"
                + "-fx-effect: dropshadow(gaussian, rgba(28,58,42,0.22), 22, 0, 0, 6);");
        Popup popup = new Popup();
        popup.getContent().add(box);
        popup.setAutoFix(true);
        box.setOnMouseClicked(e -> close(popup));
        popup.show(w);
        box.applyCss();
        box.autosize();
        OPEN.add(popup);
        reposition(w);

        box.setOpacity(0);
        FadeTransition in = new FadeTransition(Duration.millis(180), box);
        in.setToValue(1);
        TranslateTransition rise = new TranslateTransition(Duration.millis(180), box);
        rise.setFromY(12);
        rise.setToY(0);
        PauseTransition hold = new PauseTransition(Duration.seconds(kind == Kind.ERROR ? 6 : 3.6));
        FadeTransition out = new FadeTransition(Duration.millis(260), box);
        out.setToValue(0);
        SequentialTransition seq = new SequentialTransition(new javafx.animation.ParallelTransition(in, rise), hold, out);
        seq.setOnFinished(e -> close(popup));
        seq.play();
    }

    private static void close(Popup p) {
        Window owner = p.getOwnerWindow();
        p.hide();
        OPEN.remove(p);
        if (owner != null) reposition(owner);
    }

    /** Stacks open toasts upward from the bottom-right corner. */
    private static void reposition(Window w) {
        double y = w.getY() + w.getHeight() - 28;
        for (int i = OPEN.size() - 1; i >= 0; i--) {
            Popup p = OPEN.get(i);
            if (p.getOwnerWindow() != w || p.getContent().isEmpty()) continue;
            var node = p.getContent().get(0);
            double h = node.getLayoutBounds().getHeight(), wd = node.getLayoutBounds().getWidth();
            y -= h;
            p.setX(w.getX() + w.getWidth() - wd - 28);
            p.setY(y);
            y -= 10;
        }
    }

    private static Window activeWindow() {
        Window best = null;
        for (Window w : Window.getWindows()) {
            if (!w.isShowing() || w instanceof Popup || w instanceof javafx.stage.PopupWindow) continue;
            if (w.isFocused()) return w;
            if (best == null || w.getWidth() * w.getHeight() > best.getWidth() * best.getHeight()) best = w;
        }
        return best;
    }

    /** Server replies start with "SUCCESS:"; show the human part only. */
    private static String clean(String m) {
        String t = m.trim();
        if (t.startsWith("SUCCESS:")) t = t.substring(8).trim();
        else if (t.startsWith("SUCCESS")) t = t.substring(7).trim();
        return t.isEmpty() ? "Done." : t;
    }
}
