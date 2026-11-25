package storage;

import java.io.File;
import java.io.IOException;

/**
 * Resolves the application {@code data} directory in a stable way across machines and working directories.
 * <ul>
 *   <li>System property {@code lorax.data.dir} (or {@code app.data.dir}) — absolute path to the {@code data} folder</li>
 *   <li>Otherwise {@code <user.dir>/data} (canonicalized)</li>
 * </ul>
 * All JSON stores and image files should use paths under this directory.
 */
public final class AppDataPaths {

    private static final File DATA_DIRECTORY;

    static {
        String override = firstNonBlank(
                System.getProperty("lorax.data.dir"),
                System.getProperty("app.data.dir"));
        File root;
        if (override != null) {
            root = new File(override);
        } else {
            root = new File(System.getProperty("user.dir", "."), "data");
        }
        try {
            root = root.getCanonicalFile();
        } catch (IOException e) {
            root = root.getAbsoluteFile();
        }
        if (!root.isDirectory() && !root.mkdirs()) {
            System.err.println("AppDataPaths: could not create data directory: " + root);
        }
        DATA_DIRECTORY = root;
    }

    private AppDataPaths() {}

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a.trim();
        }
        if (b != null && !b.trim().isEmpty()) {
            return b.trim();
        }
        return null;
    }

    /** Absolute {@code .../data} directory (contains {@code tree_data.json}, {@code images/}, etc.). */
    public static File getDataDirectory() {
        return DATA_DIRECTORY;
    }

    public static File dataFile(String name) {
        return new File(DATA_DIRECTORY, name);
    }

    /**
     * Maps a path as stored in JSON (e.g. {@code data/images/trees/tree_1.img}) to a file under the
     * resolved data directory ({@code images/trees/tree_1.img}).
     */
    public static File resolveUnderDataDir(String storedPath) {
        if (storedPath == null || storedPath.trim().isEmpty()) {
            return null;
        }
        String n = storedPath.replace('\\', '/').trim();
        if (n.startsWith("data/")) {
            n = n.substring("data/".length());
        }
        return new File(DATA_DIRECTORY, n);
    }
}
