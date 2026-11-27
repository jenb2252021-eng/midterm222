package client_GUI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.scene.shape.TriangleMesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns OSM building footprints (Overpass JSON) into extruded 3D meshes positioned with {@link GeoProjection} and
 * standing on the {@link Terrain}. Colours follow a beige ramp by height (#d8d2c4 low to
 * #a8a294 tall). Buildings are merged into a few large meshes per colour band, so thousands of buildings render
 * as a handful of scene nodes.
 */
final class OsmBuildings {

    /** One extruded footprint in scene metres: outline (x,z) pairs and heights (metres above ground). */
    record Footprint(double[] xz, double baseHeight, double height) {}

    /** One colour band: merged wall and roof meshes and the band's colour. */
    record Band(javafx.scene.paint.Color color, List<TriangleMesh> walls, List<TriangleMesh> roofs) {}

    /** Merged meshes ready to wrap in MeshViews. */
    record Meshes(List<Band> bands, int buildingCount) {}

    /** Height bands (metres) and colours of the beige ramp. */
    private static final double[] BAND_LIMITS = {9, 18, 32, 60, Double.MAX_VALUE};
    private static final String[] BAND_COLORS = {"#d8d2c4", "#d0c9ba", "#c9c2b2", "#bcb5a6", "#a8a294"};

    private static final int MAX_POINTS_PER_MESH = 60000;

    private OsmBuildings() {}

    static List<Footprint> parse(String overpassJson, GeoProjection proj, int maxBuildings) {
        List<Footprint> out = new ArrayList<>();
        if (overpassJson == null) return out;
        try {
            JsonNode root = new ObjectMapper().readTree(overpassJson);
            JsonNode elements = root.get("elements");
            if (elements == null || !elements.isArray()) return out;
            for (JsonNode el : elements) {
                if (out.size() >= maxBuildings) break;
                if (!"way".equals(el.path("type").asText())) continue;
                JsonNode geom = el.get("geometry");
                if (geom == null || !geom.isArray() || geom.size() < 4) continue;
                int n = geom.size();
                // Overpass closes ways by repeating the first node.
                JsonNode first = geom.get(0), last = geom.get(n - 1);
                if (first.path("lat").asDouble() == last.path("lat").asDouble()
                        && first.path("lon").asDouble() == last.path("lon").asDouble()) n--;
                if (n < 3) continue;
                double[] xz = new double[n * 2];
                for (int i = 0; i < n; i++) {
                    JsonNode p = geom.get(i);
                    xz[2 * i] = proj.x(p.path("lon").asDouble());
                    xz[2 * i + 1] = proj.z(p.path("lat").asDouble());
                }
                JsonNode tags = el.path("tags");
                double h = heightFor(tags, el.path("id").asLong());
                double base = parseMeters(tags.path("min_height").asText(null));
                if (Double.isNaN(base)) {
                    double minLevel = parseMeters(tags.path("building:min_level").asText(null));
                    base = Double.isNaN(minLevel) ? 0 : minLevel * 3.2;
                }
                if (base >= h) base = 0;
                out.add(new Footprint(xz, base, h));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static double heightFor(JsonNode tags, long id) {
        double h = parseMeters(tags.path("height").asText(null));
        if (!Double.isNaN(h) && h > 1) return Math.min(h, 250);
        double levels = parseMeters(tags.path("building:levels").asText(null));
        if (!Double.isNaN(levels) && levels > 0) return Math.min(levels * 3.1, 250);
        String kind = tags.path("building").asText("yes").toLowerCase(Locale.ROOT);
        // Untagged heights: stable pseudo-random by OSM id so the skyline doesn't change between runs.
        double r = ((id * 2654435761L) & 0xffff) / 65535.0;
        return switch (kind) {
            case "house", "residential", "detached", "hut", "shed", "garage", "roof" -> 4 + r * 4;
            case "commercial", "retail", "office", "hotel", "apartments", "school", "university", "hospital" -> 9 + r * 12;
            case "church", "cathedral" -> 14 + r * 8;
            default -> 5 + r * 8;
        };
    }

    private static double parseMeters(String s) {
        if (s == null) return Double.NaN;
        String t = s.trim().toLowerCase(Locale.ROOT).replace("m", "").replace(",", ".").trim();
        if (t.isEmpty()) return Double.NaN;
        try {
            return Double.parseDouble(t.split("[ ;]")[0]);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * @param groundY scene y of the ground at (x, z) (negative is up), so each building stands on the terrain
     */
    static Meshes buildMeshes(List<Footprint> footprints, java.util.function.DoubleBinaryOperator groundY) {
        int bands = BAND_LIMITS.length;
        List<List<TriangleMesh>> walls = new ArrayList<>(), roofs = new ArrayList<>();
        MeshBuilder[] wall = new MeshBuilder[bands], roof = new MeshBuilder[bands];
        for (int i = 0; i < bands; i++) {
            walls.add(new ArrayList<>()); roofs.add(new ArrayList<>());
            wall[i] = new MeshBuilder(); roof[i] = new MeshBuilder();
        }
        int count = 0;
        for (Footprint f : footprints) {
            double[] xz = ensureCounterClockwise(f.xz());
            int n = xz.length / 2;
            int[] tris = triangulate(xz);
            if (tris == null) continue;
            int band = 0;
            while (band < bands - 1 && f.height() >= BAND_LIMITS[band]) band++;
            if (wall[band].points() > MAX_POINTS_PER_MESH) { walls.get(band).add(wall[band].build()); wall[band] = new MeshBuilder(); }
            if (roof[band].points() > MAX_POINTS_PER_MESH) { roofs.get(band).add(roof[band].build()); roof[band] = new MeshBuilder(); }
            // Stand on the lowest ground under the footprint, with walls reaching 1 m below so slopes leave no gaps.
            double ground = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) ground = Math.max(ground, groundY.applyAsDouble(xz[2 * i], xz[2 * i + 1]));
            float bottom = (float) (ground + 1.0 - f.baseHeight());
            float top = (float) (ground - f.height());
            MeshBuilder wb = wall[band];
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float x1 = (float) xz[2 * i], z1 = (float) xz[2 * i + 1];
                float x2 = (float) xz[2 * j], z2 = (float) xz[2 * j + 1];
                int a = wb.point(x1, bottom, z1);
                int b = wb.point(x2, bottom, z2);
                int c = wb.point(x2, top, z2);
                int d = wb.point(x1, top, z1);
                wb.face(a, c, b);
                wb.face(a, d, c);
            }
            MeshBuilder rb = roof[band];
            int base = rb.points();
            for (int i = 0; i < n; i++) rb.point((float) xz[2 * i], top, (float) xz[2 * i + 1]);
            for (int t = 0; t < tris.length; t += 3) rb.face(base + tris[t], base + tris[t + 1], base + tris[t + 2]);
            count++;
        }
        List<Band> out = new ArrayList<>();
        for (int i = 0; i < bands; i++) {
            if (wall[i].points() > 0) walls.get(i).add(wall[i].build());
            if (roof[i].points() > 0) roofs.get(i).add(roof[i].build());
            if (!walls.get(i).isEmpty()) out.add(new Band(javafx.scene.paint.Color.web(BAND_COLORS[i]), walls.get(i), roofs.get(i)));
        }
        return new Meshes(out, count);
    }

    /** Signed area in the x/z plane (positive = counter-clockwise when looking down from above, north = +z). */
    private static double signedArea(double[] xz) {
        double a = 0;
        int n = xz.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            a += xz[2 * i] * xz[2 * j + 1] - xz[2 * j] * xz[2 * i + 1];
        }
        return a / 2;
    }

    private static double[] ensureCounterClockwise(double[] xz) {
        if (signedArea(xz) >= 0) return xz;
        int n = xz.length / 2;
        double[] r = new double[xz.length];
        for (int i = 0; i < n; i++) {
            r[2 * i] = xz[2 * (n - 1 - i)];
            r[2 * i + 1] = xz[2 * (n - 1 - i) + 1];
        }
        return r;
    }

    /**
     * Ear-clipping triangulation of a simple counter-clockwise polygon. Returns vertex index triples
     * wound to face upward (toward -y) in the JavaFX scene, or null for degenerate input.
     */
    static int[] triangulate(double[] xz) {
        int n = xz.length / 2;
        if (n < 3) return null;
        List<Integer> idx = new ArrayList<>(n);
        for (int i = 0; i < n; i++) idx.add(i);
        int[] out = new int[(n - 2) * 3];
        int o = 0;
        int guard = 0;
        while (idx.size() > 3 && guard++ < n * n) {
            boolean clipped = false;
            for (int k = 0; k < idx.size(); k++) {
                int ip = idx.get((k + idx.size() - 1) % idx.size());
                int ic = idx.get(k);
                int in = idx.get((k + 1) % idx.size());
                if (!isConvex(xz, ip, ic, in)) continue;
                boolean contains = false;
                for (int m : idx) {
                    if (m == ip || m == ic || m == in) continue;
                    if (pointInTriangle(xz, m, ip, ic, in)) { contains = true; break; }
                }
                if (contains) continue;
                out[o++] = ip; out[o++] = in; out[o++] = ic;
                idx.remove(k);
                clipped = true;
                break;
            }
            if (!clipped) {
                // Self-intersecting or collinear outline: fall back to a fan so the roof still closes.
                int a = idx.get(0);
                for (int k = 1; k + 1 < idx.size() && o + 3 <= out.length; k++) {
                    out[o++] = a; out[o++] = idx.get(k + 1); out[o++] = idx.get(k);
                }
                idx.clear();
                break;
            }
        }
        if (idx.size() == 3 && o + 3 <= out.length) {
            out[o++] = idx.get(0); out[o++] = idx.get(2); out[o++] = idx.get(1);
        }
        if (o == out.length) return out;
        int[] trimmed = new int[o];
        System.arraycopy(out, 0, trimmed, 0, o);
        return trimmed;
    }

    private static boolean isConvex(double[] p, int a, int b, int c) {
        double cross = (p[2 * b] - p[2 * a]) * (p[2 * c + 1] - p[2 * a + 1])
                - (p[2 * b + 1] - p[2 * a + 1]) * (p[2 * c] - p[2 * a]);
        return cross > 1e-9;
    }

    private static boolean pointInTriangle(double[] p, int m, int a, int b, int c) {
        double px = p[2 * m], pz = p[2 * m + 1];
        double d1 = sign(px, pz, p[2 * a], p[2 * a + 1], p[2 * b], p[2 * b + 1]);
        double d2 = sign(px, pz, p[2 * b], p[2 * b + 1], p[2 * c], p[2 * c + 1]);
        double d3 = sign(px, pz, p[2 * c], p[2 * c + 1], p[2 * a], p[2 * a + 1]);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0;
        boolean pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }

    private static double sign(double x1, double z1, double x2, double z2, double x3, double z3) {
        return (x1 - x3) * (z2 - z3) - (x2 - x3) * (z1 - z3);
    }
}
