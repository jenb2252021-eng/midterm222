package client_GUI;

/**
 * Shared geographic coordinate system for the 3D Forest Map.
 * <p>
 * Every object in the 3D scene (OSM map tiles and registry trees) is placed with this
 * one projection, so tiles and trees always line up. It is Web Mercator (EPSG:3857, the same
 * projection the OpenStreetMap tiles and the 2D Leaflet field map use), re-centred on an origin and
 * scaled by cos(latitude) so one scene unit is about one real metre near the origin.
 * </p>
 * Scene axes: {@code x} = east, {@code z} = north, {@code y} = down (JavaFX convention, so "up" is negative y).
 */
public final class GeoProjection {

    private static final double EARTH_RADIUS = 6378137.0;
    private static final double MAX_LAT = 85.05112878;

    private final double originLat;
    private final double originLon;
    private final double originMx;
    private final double originMy;
    /** Mercator metres to true ground metres at the origin latitude. */
    private final double scale;

    public GeoProjection(double originLat, double originLon) {
        this.originLat = originLat;
        this.originLon = originLon;
        this.originMx = mercX(originLon);
        this.originMy = mercY(originLat);
        this.scale = Math.cos(Math.toRadians(originLat));
    }

    public double originLat() { return originLat; }
    public double originLon() { return originLon; }

    public static double mercX(double lon) {
        return EARTH_RADIUS * Math.toRadians(lon);
    }

    public static double mercY(double lat) {
        double l = Math.max(-MAX_LAT, Math.min(MAX_LAT, lat));
        return EARTH_RADIUS * Math.log(Math.tan(Math.PI / 4 + Math.toRadians(l) / 2));
    }

    /** Scene x (east, metres) for a longitude. */
    public double x(double lon) {
        return (mercX(lon) - originMx) * scale;
    }

    /** Scene z (north, metres) for a latitude. */
    public double z(double lat) {
        return (mercY(lat) - originMy) * scale;
    }

    public double lonAt(double x) {
        return Math.toDegrees((x / scale + originMx) / EARTH_RADIUS);
    }

    public double latAt(double z) {
        double my = z / scale + originMy;
        return Math.toDegrees(2 * Math.atan(Math.exp(my / EARTH_RADIUS)) - Math.PI / 2);
    }

    // ---- Slippy-map tile maths (OSM / Leaflet tile numbering) ----

    public static double tileXf(double lon, int zoom) {
        return (lon + 180.0) / 360.0 * (1 << zoom);
    }

    public static double tileYf(double lat, int zoom) {
        double l = Math.max(-MAX_LAT, Math.min(MAX_LAT, lat));
        double r = Math.toRadians(l);
        return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * (1 << zoom);
    }

    public static double tileLon(int x, int zoom) {
        return x / (double) (1 << zoom) * 360.0 - 180.0;
    }

    public static double tileLat(int y, int zoom) {
        double n = Math.PI - 2.0 * Math.PI * y / (1 << zoom);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    /** Approximate ground distance in metres between two lat/lon points. */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * 6371000.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
