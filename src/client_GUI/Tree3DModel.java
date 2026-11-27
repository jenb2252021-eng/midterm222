package client_GUI;

import Model.Tree;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Procedural, species-aware 3D tree models built from real registry measurements.
 * <p>
 * Shape comes from the species name (pines and other conifers get layered cone crowns, palms get fronds,
 * everything else gets a lumpy broadleaf crown whose spread depends on the species). Size comes from the
 * recorded height and trunk diameter. Health is shown through the foliage itself: healthy trees are full
 * and green, monitored trees start yellowing, at-risk trees are thin and brown, destroyed trees are a
 * snapped trunk with a fallen log. The random seed is the tree ID, so every tree keeps the same look.
 * </p>
 * Model units are metres; the base of the trunk sits at the origin and "up" is -y.
 */
final class Tree3DModel {

    enum Health { HEALTHY, MONITORING, AT_RISK, DESTROYED, UNKNOWN }

    enum Form { BROADLEAF, CONIFER, PALM }

    private static final Map<String, PhongMaterial> MATERIALS = new HashMap<>();
    private static WritableImage leafTexture;
    private static WritableImage barkTexture;
    private static float[] icoPoints;
    private static int[] icoFaces;

    private Tree3DModel() {}

    static Health health(String status) {
        String s = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (s.contains("healthy")) return Health.HEALTHY;
        if (s.contains("monitor")) return Health.MONITORING;
        if (s.contains("risk")) return Health.AT_RISK;
        if (s.contains("destroy") || s.contains("dead")) return Health.DESTROYED;
        return Health.UNKNOWN;
    }

    static Color healthColor(Health h) {
        return switch (h) {
            case HEALTHY -> Color.web("#4F772D");
            case MONITORING -> Color.web("#457B9D");
            case AT_RISK -> Color.web("#BC6C25");
            case DESTROYED -> Color.web("#9D2235");
            default -> Color.web("#6B8E23");
        };
    }

    static Form form(String species) {
        String s = species == null ? "" : species.toLowerCase(Locale.ROOT);
        if (s.matches(".*(pine|pinus|conifer|cypress|cedar|araucaria|agoho|casuarina|fir|spruce|podocarp|juniper|yew).*")) {
            return Form.CONIFER;
        }
        if (s.matches(".*(palm|coconut|niyog|anahaw|betel|bunga|buri).*")) return Form.PALM;
        return Form.BROADLEAF;
    }

    /** Crown width relative to tree height, by species (broadleaf only). */
    private static double spread(String species) {
        String s = species == null ? "" : species.toLowerCase(Locale.ROOT);
        if (s.contains("narra") || s.contains("acacia") || s.contains("rain") || s.contains("mango")) return 0.62;
        if (s.contains("mahogany") || s.contains("molave") || s.contains("banaba")) return 0.5;
        if (s.contains("alnus") || s.contains("alder") || s.contains("eucalyptus") || s.contains("gmelina")) return 0.34;
        return 0.45;
    }

    /** Effective modelled height in metres (very small or missing measurements still get a visible sapling). */
    static double modelHeight(Tree t) {
        double h = t.height > 0 ? t.height : 1.5;
        return Math.max(0.6, Math.min(h, 80));
    }

    /** Approximate crown radius in metres, used for the ground ring and picking halo. */
    static double crownRadius(Tree t) {
        double h = modelHeight(t);
        return switch (form(t.species)) {
            case CONIFER -> h * 0.24;
            case PALM -> h * 0.33;
            default -> h * spread(t.species) * 0.62;
        };
    }

    /** Builds the tree: trunk, branches and crown. */
    static Group build(Tree t) {
        Health health = health(t.status);
        Random rnd = new Random(31L * t.id + 7);
        double h = modelHeight(t);
        double trunkR = Math.max(t.diameter > 0 ? t.diameter / 200.0 : 0.04, h * 0.022);
        trunkR = Math.min(trunkR, h * 0.09);
        Group g = new Group();
        if (health == Health.DESTROYED) {
            buildDestroyed(g, h, trunkR, rnd);
            return g;
        }
        switch (form(t.species)) {
            case CONIFER -> buildConifer(g, t, h, trunkR, health, rnd);
            case PALM -> buildPalm(g, h, trunkR, health, rnd);
            default -> buildBroadleaf(g, t, h, trunkR, health, rnd);
        }
        return g;
    }

    // ------------------------------------------------------------------ species forms

    private static void buildBroadleaf(Group g, Tree t, double h, double trunkR, Health health, Random rnd) {
        double crownBase = h * (0.32 + rnd.nextDouble() * 0.08);
        double crownH = h - crownBase;
        double crownR = h * spread(t.species) * 0.62;
        // Trunk runs into the crown.
        g.getChildren().add(trunk(trunkR, trunkR * 0.45, crownBase + crownH * 0.45, rnd, 0.012 * h));
        // Main limbs.
        int limbs = 3 + rnd.nextInt(2);
        for (int i = 0; i < limbs; i++) {
            double ang = i * (360.0 / limbs) + rnd.nextDouble() * 30;
            double len = crownR * (0.7 + rnd.nextDouble() * 0.3);
            g.getChildren().add(limb(trunkR * 0.45, crownBase * (0.85 + rnd.nextDouble() * 0.25), len, ang, 40 + rnd.nextDouble() * 15, rnd));
        }
        double density = switch (health) { case HEALTHY -> 1.0; case MONITORING -> 0.85; case AT_RISK -> 0.5; default -> 0.9; };
        int blobs = (int) Math.round((9 + rnd.nextInt(4)) * density);
        Color[] shades = foliageShades(health, false);
        MeshBuilder[] mb = new MeshBuilder[shades.length];
        for (int i = 0; i < mb.length; i++) mb[i] = new MeshBuilder();
        double cy = crownBase + crownH * 0.52;
        // Central mass plus clusters distributed over an ellipsoid shell.
        blob(mb[0], 0, -cy, 0, crownR * 0.72 * Math.sqrt(density), crownH * 0.42, crownR * 0.72 * Math.sqrt(density), rnd);
        for (int i = 0; i < blobs; i++) {
            double theta = rnd.nextDouble() * Math.PI * 2;
            double phi = Math.acos(1 - rnd.nextDouble() * 1.3); // bias toward upper hemisphere
            double r = 0.62 + rnd.nextDouble() * 0.2;
            double bx = Math.sin(phi) * Math.cos(theta) * crownR * r;
            double bz = Math.sin(phi) * Math.sin(theta) * crownR * r;
            double by = cy + Math.cos(phi) * crownH * 0.42 * r - crownH * 0.08;
            double size = crownR * (0.34 + rnd.nextDouble() * 0.16) * (health == Health.AT_RISK ? 0.8 : 1);
            blob(mb[1 + i % (mb.length - 1)], bx, -by, bz, size, size * 0.82, size, rnd);
        }
        for (int i = 0; i < mb.length; i++) {
            if (mb[i].points() > 0) g.getChildren().add(view(mb[i].build(), leafMaterial(shades[i])));
        }
    }

    private static void buildConifer(Group g, Tree t, double h, double trunkR, Health health, Random rnd) {
        g.getChildren().add(trunk(trunkR, trunkR * 0.25, h * 0.97, rnd, 0.015 * h));
        boolean isPine = t.species != null && t.species.toLowerCase(Locale.ROOT).contains("pine");
        int tiers = isPine ? 6 : 7;
        double crownBase = h * (isPine ? 0.32 : 0.18);
        double maxR = h * (isPine ? 0.26 : 0.22);
        double density = switch (health) { case HEALTHY -> 1.0; case MONITORING -> 0.85; case AT_RISK -> 0.55; default -> 0.9; };
        Color[] shades = foliageShades(health, true);
        MeshBuilder[] mb = new MeshBuilder[shades.length];
        for (int i = 0; i < mb.length; i++) mb[i] = new MeshBuilder();
        double span = h - crownBase;
        for (int i = 0; i < tiers; i++) {
            if (health == Health.AT_RISK && i % 2 == 1 && i < tiers - 1) continue; // missing tiers
            double f = i / (double) tiers;
            double y0 = crownBase + span * f * 0.92;
            double tierH = span / tiers * 1.9;
            double r = maxR * (1 - f * 0.82) * (0.9 + rnd.nextDouble() * 0.2) * Math.sqrt(density);
            if (isPine) r *= (i % 2 == 0 ? 1.0 : 0.8);
            cone(mb[i % mb.length], 0, y0, y0 + tierH, r, 14, rnd);
        }
        for (int i = 0; i < mb.length; i++) {
            if (mb[i].points() > 0) g.getChildren().add(view(mb[i].build(), leafMaterial(shades[i])));
        }
    }

    private static void buildPalm(Group g, double h, double trunkR, Health health, Random rnd) {
        // Gently curved trunk made of stacked segments.
        double lean = 4 + rnd.nextDouble() * 6;
        double lean2 = rnd.nextDouble() * 360;
        Group leaning = new Group(trunk(trunkR * 0.9, trunkR * 0.7, h * 0.92, rnd, 0.0));
        int fronds = health == Health.AT_RISK ? 6 : 10;
        Color[] shades = foliageShades(health, false);
        MeshBuilder mb = new MeshBuilder();
        double len = h * 0.42;
        double droop = health == Health.AT_RISK ? 1.6 : 1.0;
        for (int i = 0; i < fronds; i++) {
            double ang = Math.toRadians(i * 360.0 / fronds + rnd.nextDouble() * 12);
            frond(mb, ang, len * (0.85 + rnd.nextDouble() * 0.3), len * 0.16, droop, rnd);
        }
        MeshView crown = view(mb.build(), leafMaterial(shades[1]));
        crown.setCullFace(javafx.scene.shape.CullFace.NONE);
        crown.setTranslateY(-h * 0.92);
        leaning.getChildren().add(crown);
        // Crown rides on the trunk, so the whole palm leans together.
        leaning.getTransforms().addAll(new Rotate(lean2, Rotate.Y_AXIS), new Rotate(lean, Rotate.Z_AXIS));
        g.getChildren().add(leaning);
    }

    private static void buildDestroyed(Group g, double h, double trunkR, Random rnd) {
        double stumpH = Math.max(0.3, h * 0.28);
        MeshBuilder mb = new MeshBuilder();
        tube(mb, trunkR * 1.05, trunkR * 0.9, 0, stumpH, 10, 3, rnd, 0, true);
        // Jagged break on top.
        int seg = 10;
        int center = mb.point(0, (float) -(stumpH + h * 0.03), 0, 0.5f, 0.5f);
        int first = -1, prev = -1;
        for (int i = 0; i <= seg; i++) {
            double a = 2 * Math.PI * i / seg;
            double jag = stumpH + (rnd.nextDouble() - 0.3) * h * 0.08;
            int p = mb.point((float) (Math.cos(a) * trunkR * 0.9), (float) -jag, (float) (Math.sin(a) * trunkR * 0.9), (float) i / seg, 0);
            if (prev >= 0) mb.face(center, prev, p);
            if (first < 0) first = p;
            prev = p;
        }
        PhongMaterial dead = barkMaterial(Color.web("#6B5B4E"));
        g.getChildren().add(view(mb.build(), dead));
        // Fallen trunk lying beside the stump.
        MeshBuilder log = new MeshBuilder();
        tube(log, trunkR * 1.1, trunkR * 0.5, 0, h * 0.65, 10, 4, rnd, 0, false);
        MeshView logView = view(log.build(), dead);
        Group logGroup = new Group(logView);
        logGroup.getTransforms().addAll(new Rotate(rnd.nextDouble() * 360, Rotate.Y_AXIS), new Rotate(86, Rotate.X_AXIS));
        logGroup.setTranslateY(-trunkR * 0.8);
        logGroup.setTranslateX(trunkR * 1.6);
        g.getChildren().add(logGroup);
    }

    // ------------------------------------------------------------------ indicator geometry

    /** Flat ring on the ground (annulus), used for the health ring and the selection halo. */
    static MeshView ring(double inner, double outer, Color color, double lift) {
        MeshBuilder mb = new MeshBuilder();
        int seg = 48;
        for (int i = 0; i <= seg; i++) {
            double a = 2 * Math.PI * i / seg;
            mb.point((float) (Math.cos(a) * inner), (float) -lift, (float) (Math.sin(a) * inner));
            mb.point((float) (Math.cos(a) * outer), (float) -lift, (float) (Math.sin(a) * outer));
        }
        for (int i = 0; i < seg; i++) {
            int a = 2 * i, b = a + 1, c = a + 2, d = a + 3;
            mb.face(a, c, b);
            mb.face(b, c, d);
        }
        MeshView v = view(mb.build(), flat(color));
        v.setCullFace(javafx.scene.shape.CullFace.NONE);
        return v;
    }

    /** Map-pin style beacon (sphere head + cone tip) in the tree's health colour, tip at the origin. */
    static Group beacon(Color color) {
        MeshBuilder mb = new MeshBuilder();
        Random r = new Random(1);
        blob(mb, 0, -1.55, 0, 0.55, 0.55, 0.55, r, 0);
        cone(mb, 0, 1.45, 0, 0.42, 12, null); // rim at the head, apex on the ground (pin tip)
        MeshView v = view(mb.build(), flat(color));
        v.setCullFace(javafx.scene.shape.CullFace.NONE);
        MeshBuilder dot = new MeshBuilder();
        blob(dot, 0, -1.55, 0, 0.24, 0.24, 0.24, r, 0);
        MeshView inner = view(dot.build(), flat(Color.WHITE));
        inner.setTranslateZ(-0.38);
        Group pin = new Group(v, inner);
        return pin;
    }

    // ------------------------------------------------------------------ primitives

    private static Node trunk(double r0, double r1, double height, Random rnd, double wobble) {
        MeshBuilder mb = new MeshBuilder();
        tube(mb, r0, r1, 0, height, 10, 6, rnd, wobble, false);
        // Root flare.
        tube(mb, r0 * 1.6, r0, 0, Math.min(height * 0.08, r0 * 3), 10, 1, rnd, 0, false);
        return view(mb.build(), barkMaterial(Color.web("#6F5440")));
    }

    private static Node limb(double r, double startY, double length, double headingDeg, double tiltDeg, Random rnd) {
        MeshBuilder mb = new MeshBuilder();
        tube(mb, r, r * 0.35, 0, length, 7, 2, rnd, 0, false);
        MeshView v = view(mb.build(), barkMaterial(Color.web("#6F5440")));
        Group g = new Group(v);
        g.getTransforms().addAll(new Rotate(headingDeg, Rotate.Y_AXIS), new Rotate(tiltDeg, Rotate.Z_AXIS));
        g.setTranslateY(-startY);
        return g;
    }

    /** Tapered tube from y0 to y1 (metres above ground), optionally wobbling sideways. */
    private static void tube(MeshBuilder mb, double r0, double r1, double y0, double y1, int seg, int rings,
                             Random rnd, double wobble, boolean capBottom) {
        int base = mb.points();
        double ox = 0, oz = 0;
        for (int k = 0; k <= rings; k++) {
            double f = k / (double) rings;
            double r = r0 + (r1 - r0) * f;
            double y = y0 + (y1 - y0) * f;
            if (k > 0 && wobble > 0) { ox += (rnd.nextDouble() - 0.5) * wobble; oz += (rnd.nextDouble() - 0.5) * wobble; }
            for (int i = 0; i <= seg; i++) {
                double a = 2 * Math.PI * i / seg;
                double rr = r * (0.94 + 0.12 * Math.abs(Math.sin(a * 3 + k)));
                mb.point((float) (ox + Math.cos(a) * rr), (float) -y, (float) (oz + Math.sin(a) * rr),
                        (float) i / seg, (float) (f * (y1 - y0) / Math.max(0.2, r0 * 6)));
            }
        }
        int row = seg + 1;
        for (int k = 0; k < rings; k++) {
            for (int i = 0; i < seg; i++) {
                int a = base + k * row + i, b = a + 1, c = a + row, d = c + 1;
                mb.face(a, b, c);
                mb.face(b, d, c);
            }
        }
    }

    /** Cone (or tier) with a slightly ragged rim, from yBase (rim) to yTop (apex), in metres above ground. */
    private static void cone(MeshBuilder mb, double cx, double yBase, double yTop, double r, int seg, Random rnd) {
        int apex = mb.point((float) cx, (float) -yTop, 0, 0.5f, 0f);
        int base = mb.points();
        for (int i = 0; i <= seg; i++) {
            double a = 2 * Math.PI * i / seg;
            double rr = r * (rnd == null ? 1 : (i % 2 == 0 ? 1.0 : 0.78 + rnd.nextDouble() * 0.12));
            double droop = rnd == null ? 0 : (i % 2 == 0 ? r * 0.18 : 0);
            mb.point((float) (cx + Math.cos(a) * rr), (float) -(yBase - droop), (float) (Math.sin(a) * rr), (float) i / seg, 1f);
        }
        int center = mb.point((float) cx, (float) -(yBase + (yTop - yBase) * 0.12), 0, 0.5f, 0.5f);
        for (int i = 0; i < seg; i++) {
            mb.face(apex, base + i + 1, base + i);
            mb.face(center, base + i, base + i + 1);
        }
    }

    private static void frond(MeshBuilder mb, double heading, double len, double width, double droop, Random rnd) {
        int steps = 6;
        double cx = Math.cos(heading), cz = Math.sin(heading);
        double px = -cz, pz = cx;
        int prevL = -1, prevR = -1, prevC = -1;
        for (int s = 0; s <= steps; s++) {
            double f = s / (double) steps;
            double d = f * len;
            double y = len * 0.25 * f - droop * len * 0.5 * f * f;
            double w = width * Math.sin(Math.PI * Math.min(1, f * 1.1 + 0.05));
            float x = (float) (cx * d), z = (float) (cz * d);
            int c = mb.point(x, (float) -y, z, (float) f, 0.5f);
            int l = mb.point((float) (x + px * w), (float) -(y - w * 0.35), (float) (z + pz * w), (float) f, 0f);
            int r = mb.point((float) (x - px * w), (float) -(y - w * 0.35), (float) (z - pz * w), (float) f, 1f);
            if (prevC >= 0) {
                mb.face(prevC, prevL, l); mb.face(prevC, l, c);
                mb.face(prevC, c, r); mb.face(prevC, r, prevR);
            }
            prevC = c; prevL = l; prevR = r;
        }
    }

    private static void blob(MeshBuilder mb, double cx, double cy, double cz, double rx, double ry, double rz, Random rnd) {
        blob(mb, cx, cy, cz, rx, ry, rz, rnd, 0.22);
    }

    /** Noise-displaced icosphere: a lumpy foliage cluster. (cy is already in scene y, i.e. negative = up.) */
    private static void blob(MeshBuilder mb, double cx, double cy, double cz, double rx, double ry, double rz, Random rnd, double noise) {
        ensureIcosphere();
        int base = mb.points();
        double p1 = rnd.nextDouble() * 10, p2 = rnd.nextDouble() * 10, p3 = rnd.nextDouble() * 10;
        int n = icoPoints.length / 3;
        for (int i = 0; i < n; i++) {
            double x = icoPoints[3 * i], y = icoPoints[3 * i + 1], z = icoPoints[3 * i + 2];
            double d = 1 + noise * (Math.sin(x * 4.1 + p1) * Math.sin(y * 3.7 + p2) * Math.sin(z * 4.3 + p3)
                    + 0.5 * Math.sin(x * 9.3 + p2) * Math.sin(z * 8.1 + p1));
            float u = (float) (0.5 + Math.atan2(z, x) / (2 * Math.PI)) * 3;
            float v = (float) (0.5 - Math.asin(Math.max(-1, Math.min(1, y))) / Math.PI) * 3;
            mb.point((float) (cx + x * rx * d), (float) (cy + y * ry * d), (float) (cz + z * rz * d), u, v);
        }
        for (int f = 0; f < icoFaces.length; f += 3) {
            mb.face(base + icoFaces[f], base + icoFaces[f + 1], base + icoFaces[f + 2]);
        }
    }

    private static synchronized void ensureIcosphere() {
        if (icoPoints != null) return;
        double t = (1 + Math.sqrt(5)) / 2;
        List<double[]> v = new ArrayList<>();
        double[][] init = {{-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
                {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}};
        for (double[] p : init) v.add(norm(p));
        int[][] f = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6},
                {7, 1, 8}, {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
        List<int[]> faces = new ArrayList<>(List.of(f));
        for (int level = 0; level < 2; level++) {
            Map<Long, Integer> mid = new HashMap<>();
            List<int[]> next = new ArrayList<>();
            for (int[] tri : faces) {
                int a = midpoint(v, mid, tri[0], tri[1]);
                int b = midpoint(v, mid, tri[1], tri[2]);
                int c = midpoint(v, mid, tri[2], tri[0]);
                next.add(new int[]{tri[0], a, c});
                next.add(new int[]{tri[1], b, a});
                next.add(new int[]{tri[2], c, b});
                next.add(new int[]{a, b, c});
            }
            faces = next;
        }
        float[] pts = new float[v.size() * 3];
        for (int i = 0; i < v.size(); i++) {
            pts[3 * i] = (float) v.get(i)[0]; pts[3 * i + 1] = (float) v.get(i)[1]; pts[3 * i + 2] = (float) v.get(i)[2];
        }
        int[] fi = new int[faces.size() * 3];
        for (int i = 0; i < faces.size(); i++) {
            // Swap winding so faces point outward in JavaFX's y-down space.
            fi[3 * i] = faces.get(i)[0]; fi[3 * i + 1] = faces.get(i)[2]; fi[3 * i + 2] = faces.get(i)[1];
        }
        icoPoints = pts;
        icoFaces = fi;
    }

    private static int midpoint(List<double[]> v, Map<Long, Integer> cache, int a, int b) {
        long key = ((long) Math.min(a, b) << 32) | Math.max(a, b);
        Integer hit = cache.get(key);
        if (hit != null) return hit;
        double[] p = v.get(a), q = v.get(b);
        v.add(norm(new double[]{(p[0] + q[0]) / 2, (p[1] + q[1]) / 2, (p[2] + q[2]) / 2}));
        cache.put(key, v.size() - 1);
        return v.size() - 1;
    }

    private static double[] norm(double[] p) {
        double l = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
        return new double[]{p[0] / l, p[1] / l, p[2] / l};
    }

    // ------------------------------------------------------------------ materials

    private static Color[] foliageShades(Health health, boolean conifer) {
        Color[] base = conifer
                ? new Color[]{Color.web("#2E5A34"), Color.web("#35663A"), Color.web("#29502F")}
                : new Color[]{Color.web("#3D7A35"), Color.web("#4C8C3A"), Color.web("#5C9A44"), Color.web("#346B30")};
        Color tint = switch (health) {
            case MONITORING -> Color.web("#9AA63A");
            case AT_RISK -> Color.web("#A27B3A");
            default -> null;
        };
        double amount = health == Health.MONITORING ? 0.28 : health == Health.AT_RISK ? 0.62 : 0;
        Color[] out = new Color[base.length];
        for (int i = 0; i < base.length; i++) {
            out[i] = tint == null ? base[i] : base[i].interpolate(tint, amount + (i == 1 ? 0.12 : 0));
        }
        return out;
    }

    private static MeshView view(TriangleMesh mesh, PhongMaterial m) {
        MeshView v = new MeshView(mesh);
        v.setMaterial(m);
        return v;
    }

    static synchronized PhongMaterial flat(Color c) {
        return MATERIALS.computeIfAbsent("flat" + c, k -> {
            PhongMaterial m = new PhongMaterial(c);
            m.setSpecularColor(Color.gray(0.35));
            m.setSpecularPower(18);
            return m;
        });
    }

    private static synchronized PhongMaterial leafMaterial(Color c) {
        return MATERIALS.computeIfAbsent("leaf" + c, k -> {
            PhongMaterial m = new PhongMaterial(c);
            m.setDiffuseMap(leafTexture());
            m.setSpecularColor(Color.gray(0.08));
            m.setSpecularPower(6);
            return m;
        });
    }

    private static synchronized PhongMaterial barkMaterial(Color c) {
        return MATERIALS.computeIfAbsent("bark" + c, k -> {
            PhongMaterial m = new PhongMaterial(c);
            m.setDiffuseMap(barkTexture());
            m.setSpecularColor(Color.gray(0.05));
            return m;
        });
    }

    /** Light, speckled leaf texture multiplied by each material's diffuse colour. */
    private static WritableImage leafTexture() {
        if (leafTexture != null) return leafTexture;
        int s = 128;
        WritableImage img = new WritableImage(s, s);
        PixelWriter w = img.getPixelWriter();
        Random r = new Random(42);
        double[][] v = new double[s][s];
        for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) v[y][x] = 0.78 + r.nextDouble() * 0.22;
        for (int i = 0; i < 900; i++) { // leaf clusters: brighter and darker dabs
            int cx = r.nextInt(s), cy = r.nextInt(s), rad = 1 + r.nextInt(3);
            double val = r.nextBoolean() ? 1.0 : 0.55;
            for (int dy = -rad; dy <= rad; dy++) for (int dx = -rad; dx <= rad; dx++) {
                if (dx * dx + dy * dy > rad * rad) continue;
                v[(cy + dy + s) % s][(cx + dx + s) % s] = val * (0.9 + r.nextDouble() * 0.1);
            }
        }
        for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) w.setColor(x, y, Color.gray(Math.min(1, v[y][x])));
        leafTexture = img;
        return img;
    }

    /** Vertical bark streaks. */
    private static WritableImage barkTexture() {
        if (barkTexture != null) return barkTexture;
        int s = 64;
        WritableImage img = new WritableImage(s, s * 2);
        PixelWriter w = img.getPixelWriter();
        Random r = new Random(7);
        double[] col = new double[s];
        for (int x = 0; x < s; x++) col[x] = 0.7 + r.nextDouble() * 0.3;
        for (int y = 0; y < s * 2; y++) {
            for (int x = 0; x < s; x++) {
                double ridge = 0.75 + 0.25 * Math.sin(x * 0.9 + Math.sin(y * 0.15) * 1.5);
                double g = col[x] * ridge * (0.9 + r.nextDouble() * 0.1);
                w.setColor(x, y, Color.gray(Math.min(1, g)));
            }
        }
        barkTexture = img;
        return img;
    }
}
