package client_GUI;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats {@code lat,lon} coordinates stored in {@link Model.Tree#location}.
 */
public final class GeoCoordFormat {
    private static final Pattern LL = Pattern.compile(
            "^\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*$");

    private GeoCoordFormat() {}

    public static String format(double lat, double lon) {
        return String.format(Locale.US, "%.6f,%.6f", lat, lon);
    }

    /**
     * @return {{@code lat, lon}} or null if string is not a coordinate pair
     */
    public static double[] tryParse(String location) {
        if (location == null) {
            return null;
        }
        Matcher m = LL.matcher(location.trim());
        if (!m.matches()) {
            return null;
        }
        try {
            double lat = Double.parseDouble(m.group(1));
            double lon = Double.parseDouble(m.group(2));
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                return null;
            }
            return new double[]{lat, lon};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
