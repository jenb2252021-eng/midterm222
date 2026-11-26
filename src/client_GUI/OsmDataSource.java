package client_GUI;

import storage.AppDataPaths;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;

/**
 * Map data for the 3D Forest Map, all from free OpenStreetMap-based services:
 * <ul>
 *   <li>Basemap tiles ({@link Basemap}): the standard OpenStreetMap tiles (the same tiles as the pin picker,
 *       no API key needed) and satellite imagery (Esri).</li>
 *   <li>Elevation tiles (Terrarium-encoded, Mapzen/AWS open terrain) for the hills of Baguio.</li>
 *   <li>Building footprints from the Overpass API (OSM {@code building=*}), extruded to 3D.</li>
 * </ul>
 * Everything downloaded is cached under {@code data/cache/osm}, so the map works offline for areas already viewed.
 * In offline mode only the cache (and, for the standard style, the tiles bundled at {@code /map_web/tiles}) is used.
 * Requests carry an identifying User-Agent and the map keeps at most two tile downloads in flight.
 */
final class OsmDataSource {

    /** Basemap styles. Each keeps its own cache folder. */
    enum Basemap {
        STANDARD("OpenStreetMap", "tiles", "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                "© OpenStreetMap contributors"),
        SATELLITE("Satellite", "tiles-satellite",
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
                "Imagery © Esri, Maxar, Earthstar Geographics");

        final String label, cacheFolder, urlTemplate, attribution;

        Basemap(String label, String cacheFolder, String urlTemplate, String attribution) {
            this.label = label;
            this.cacheFolder = cacheFolder;
            this.urlTemplate = urlTemplate;
            this.attribution = attribution;
        }

        String url(int z, int x, int y) {
            String s = String.valueOf("abcd".charAt(Math.floorMod(x + y, 4)));
            return urlTemplate.replace("{s}", s).replace("{z}", String.valueOf(z))
                    .replace("{x}", String.valueOf(x)).replace("{y}", String.valueOf(y));
        }
    }

    private static final String USER_AGENT = "TheLorax-ForestRegistry/1.0 (Baguio forest registry; student project)";
    private static final String DEM_URL = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/%d/%d/%d.png";
    private static final String[] OVERPASS_URLS = {
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
    };
    private static final long TILE_MAX_AGE_MS = 30L * 24 * 3600 * 1000;

    private static final File CACHE_DIR = new File(AppDataPaths.getDataDirectory(), "cache/osm");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private OsmDataSource() {}

    /** PNG/JPEG bytes for one basemap tile, or null when it is neither cached, bundled nor downloadable. */
    static byte[] tile(Basemap style, int z, int x, int y, boolean offline) {
        File cached = new File(CACHE_DIR, style.cacheFolder + "/" + z + "/" + x + "/" + y + ".png");
        byte[] bundled = style == Basemap.STANDARD ? bundledTile(z, x, y) : null;
        return fetchCached(cached, style.url(z, x, y), offline, bundled);
    }

    /** Terrarium elevation tile (PNG), or null. Elevation never changes, so cached copies never expire. */
    static byte[] demTile(int z, int x, int y, boolean offline) {
        File cached = new File(CACHE_DIR, "dem/" + z + "/" + x + "/" + y + ".png");
        if (cached.isFile()) {
            byte[] b = readFile(cached);
            if (b != null) return b;
        }
        return fetchCached(cached, String.format(Locale.US, DEM_URL, z, x, y), offline, null);
    }

    private static byte[] fetchCached(File cached, String url, boolean offline, byte[] bundled) {
        boolean fresh = cached.isFile() && System.currentTimeMillis() - cached.lastModified() < TILE_MAX_AGE_MS;
        if (cached.isFile() && (fresh || offline)) {
            byte[] b = readFile(cached);
            if (b != null) return b;
        }
        if (offline) return bundled;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", USER_AGENT)
                    .GET().build();
            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().length > 0) {
                writeAtomically(cached, resp.body());
                return resp.body();
            }
        } catch (IOException | InterruptedException | RuntimeException ignored) {
            if (Thread.currentThread().isInterrupted()) return null;
        }
        if (cached.isFile()) return readFile(cached); // stale but better than nothing
        return bundled;
    }

    private static byte[] bundledTile(int z, int x, int y) {
        try (InputStream in = OsmDataSource.class.getResourceAsStream("/map_web/tiles/" + z + "/" + x + "/" + y + ".png")) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /** Raw Overpass JSON with OSM building footprints inside the bounding box, or null when unavailable. */
    static String buildingsJson(double south, double west, double north, double east, boolean offline) {
        String key = String.format(Locale.US, "buildings_%.4f_%.4f_%.4f_%.4f.json", south, west, north, east);
        File cached = new File(CACHE_DIR, key);
        if (cached.isFile()) {
            byte[] b = readFile(cached);
            if (b != null && b.length > 0) return new String(b, StandardCharsets.UTF_8);
        }
        String covering = findCoveringBuildingsCache(south, west, north, east);
        if (covering != null) return covering;
        if (offline) return null;
        String query = String.format(Locale.US,
                "[out:json][timeout:40];(way[\"building\"](%.6f,%.6f,%.6f,%.6f););out geom tags;",
                south, west, north, east);
        String body = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        for (String endpoint : OVERPASS_URLS) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(60))
                        .header("User-Agent", USER_AGENT)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 200 && resp.body() != null && resp.body().contains("\"elements\"")) {
                    writeAtomically(cached, resp.body().getBytes(StandardCharsets.UTF_8));
                    return resp.body();
                }
            } catch (IOException | InterruptedException | RuntimeException ignored) {
                if (Thread.currentThread().isInterrupted()) return null;
            }
        }
        return null;
    }

    private static String findCoveringBuildingsCache(double s, double w, double n, double e) {
        File[] files = CACHE_DIR.listFiles((dir, name) -> name.startsWith("buildings_") && name.endsWith(".json"));
        if (files == null) return null;
        for (File f : files) {
            String[] p = f.getName().substring("buildings_".length(), f.getName().length() - 5).split("_");
            if (p.length != 4) continue;
            try {
                double cs = Double.parseDouble(p[0]), cw = Double.parseDouble(p[1]);
                double cn = Double.parseDouble(p[2]), ce = Double.parseDouble(p[3]);
                if (cs <= s && cw <= w && cn >= n && ce >= e) {
                    byte[] b = readFile(f);
                    if (b != null) return new String(b, StandardCharsets.UTF_8);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static byte[] readFile(File f) {
        try {
            return Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeAtomically(File target, byte[] data) {
        try {
            Files.createDirectories(target.getParentFile().toPath());
            File tmp = new File(target.getParentFile(), target.getName() + ".part");
            Files.write(tmp.toPath(), data);
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException ignored) {
        }
    }
}
