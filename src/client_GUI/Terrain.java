package client_GUI;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Ground elevation for the 3D Forest Map, from open Terrarium elevation tiles (the same open terrain source
 * web maps use). Heights are returned in scene metres relative to the elevation at the map origin and
 * multiplied by a small vertical exaggeration, so Baguio's ridges and valleys read clearly. Missing tiles fall back to
 * flat ground, so the map always works.
 */
final class Terrain {

    static final double EXAGGERATION = 1.15;
    private static final Terrain FLAT = new Terrain(0, new HashMap<>(), 0);

    private final int zoom;
    private final Map<Long, float[]> tiles; // 256×256 elevations in metres
    private final double base;

    private Terrain(int zoom, Map<Long, float[]> tiles, double base) {
        this.zoom = zoom;
        this.tiles = tiles;
        this.base = base;
    }

    static Terrain flat() {
        return FLAT;
    }

    boolean isFlat() {
        return tiles.isEmpty();
    }

    /**
     * Loads every elevation tile covering the box (south, west, north, east) at a zoom that keeps the download small.
     * Runs on a background thread. Returns flat terrain if nothing could be loaded.
     */
    static Terrain load(double s, double w, double n, double e, double originLat, double originLon, boolean offline) {
        int z = 14;
        while (z > 9) {
            int count = (int) ((Math.floor(GeoProjection.tileXf(e, z)) - Math.floor(GeoProjection.tileXf(w, z)) + 1)
                    * (Math.floor(GeoProjection.tileYf(s, z)) - Math.floor(GeoProjection.tileYf(n, z)) + 1));
            if (count <= 25) break;
            z--;
        }
        int x0 = (int) Math.floor(GeoProjection.tileXf(w, z)), x1 = (int) Math.floor(GeoProjection.tileXf(e, z));
        int y0 = (int) Math.floor(GeoProjection.tileYf(n, z)), y1 = (int) Math.floor(GeoProjection.tileYf(s, z));
        Map<Long, float[]> loaded = new HashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "Terrain"); t.setDaemon(true); return t; });
        Map<Long, Future<float[]>> jobs = new HashMap<>();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                int tx = x, ty = y, tz = z;
                jobs.put(key(x, y), pool.submit(() -> decode(OsmDataSource.demTile(tz, tx, ty, offline))));
            }
        }
        for (Map.Entry<Long, Future<float[]>> j : jobs.entrySet()) {
            try {
                float[] h = j.getValue().get();
                if (h != null) loaded.put(j.getKey(), h);
            } catch (Exception ignored) {
            }
        }
        pool.shutdownNow();
        if (loaded.isEmpty()) return FLAT;
        Terrain t = new Terrain(z, loaded, 0);
        double originElev = t.rawElevation(originLat, originLon);
        return new Terrain(z, loaded, Double.isNaN(originElev) ? 0 : originElev);
    }

    /** Terrarium encoding: metres = R×256 + G + B/256 − 32768. */
    private static float[] decode(byte[] png) {
        if (png == null) return null;
        Image img = new Image(new ByteArrayInputStream(png));
        if (img.isError() || img.getWidth() < 256 || img.getHeight() < 256) return null;
        PixelReader pr = img.getPixelReader();
        float[] h = new float[256 * 256];
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                int argb = pr.getArgb(x, y);
                int r = (argb >> 16) & 0xff, g = (argb >> 8) & 0xff, b = argb & 0xff;
                h[y * 256 + x] = (float) (r * 256 + g + b / 256.0 - 32768);
            }
        }
        return h;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    /** Height above the map origin in scene metres (exaggerated), or 0 where no elevation data exists. */
    double height(double lat, double lon) {
        if (tiles.isEmpty()) return 0;
        double e = rawElevation(lat, lon);
        return Double.isNaN(e) ? 0 : (e - base) * EXAGGERATION;
    }

    /** Bilinear elevation in metres above sea level, or NaN outside the loaded tiles. */
    private double rawElevation(double lat, double lon) {
        double fx = GeoProjection.tileXf(lon, zoom) * 256 - 0.5;
        double fy = GeoProjection.tileYf(lat, zoom) * 256 - 0.5;
        int px = (int) Math.floor(fx), py = (int) Math.floor(fy);
        double ax = fx - px, ay = fy - py;
        double h00 = sample(px, py), h10 = sample(px + 1, py), h01 = sample(px, py + 1), h11 = sample(px + 1, py + 1);
        if (Double.isNaN(h00) || Double.isNaN(h10) || Double.isNaN(h01) || Double.isNaN(h11)) {
            double any = !Double.isNaN(h00) ? h00 : !Double.isNaN(h10) ? h10 : !Double.isNaN(h01) ? h01 : h11;
            return any;
        }
        return (h00 * (1 - ax) + h10 * ax) * (1 - ay) + (h01 * (1 - ax) + h11 * ax) * ay;
    }

    private double sample(int px, int py) {
        int tx = Math.floorDiv(px, 256), ty = Math.floorDiv(py, 256);
        float[] t = tiles.get(key(tx, ty));
        if (t == null) return Double.NaN;
        return t[Math.floorMod(py, 256) * 256 + Math.floorMod(px, 256)];
    }
}
