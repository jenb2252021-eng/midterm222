package storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Helper for managing image files stored under {@link AppDataPaths#getDataDirectory()}/images.
 */
public final class ImageStorage {
    private static final File BASE_DIR = new File(AppDataPaths.getDataDirectory(), "images");
    private static final File TREES_DIR = new File(BASE_DIR, "trees");
    private static final File PENDING_DIR = new File(BASE_DIR, "pending");
    private static final File DELETED_DIR = new File(BASE_DIR, "deleted");
    private static final File AVATARS_DIR = new File(BASE_DIR, "avatars");

    static {
        ensureDirectories();
    }

    private static void ensureDirectories() {
        TREES_DIR.mkdirs();
        PENDING_DIR.mkdirs();
        DELETED_DIR.mkdirs();
        AVATARS_DIR.mkdirs();
    }

    private static String writeFile(File directory, String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        ensureDirectories();
        File target = new File(directory, filename);
        try {
            Files.write(target.toPath(), bytes);
            return target.getPath();
        } catch (IOException e) {
            throw new RuntimeException("Unable to write image file: " + target, e);
        }
    }

    public static String storeTreeImage(int id, byte[] bytes) {
        return writeFile(TREES_DIR, "tree_" + id + ".img", bytes);
    }

    public static String storePendingImage(int id, byte[] bytes) {
        return writeFile(PENDING_DIR, "pending_" + id + ".img", bytes);
    }

    public static String storeDeletedImage(int id, byte[] bytes) {
        return writeFile(DELETED_DIR, "deleted_" + id + ".img", bytes);
    }

    public static String storeAvatar(String username, byte[] bytes) {
        if (username == null) return null;
        return writeFile(AVATARS_DIR, "avatar_" + username + ".img", bytes);
    }

    /**
     * Normalizes stored JSON paths (often {@code data\images\...} on Windows) for the current OS.
     */
    public static String normalizeImagePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        return path.replace('\\', File.separatorChar).replace('/', File.separatorChar);
    }

    /** True if {@code path} refers to a pending submission raster (never delete tree images using this). */
    public static boolean isPendingStoredPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String n = path.replace('\\', '/');
        return n.contains("/pending/") || n.contains("images/pending/") || n.contains("pending_");
    }

    /**
     * Locates an image file from a JSON-stored or absolute path (tries data dir first, then CWD).
     */
    public static File resolveExistingImageFile(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String trimmed = path.trim();
        File asAbs = new File(normalizeImagePath(trimmed));
        if (asAbs.isAbsolute() && asAbs.isFile()) {
            return asAbs;
        }
        String n = trimmed.replace('\\', '/');
        File underData = AppDataPaths.resolveUnderDataDir(n);
        if (underData != null && underData.isFile()) {
            return underData;
        }
        File cwd = new File(normalizeImagePath(trimmed));
        if (cwd.isFile()) {
            return cwd;
        }
        return null;
    }

    public static byte[] loadImage(String path) {
        File file = resolveExistingImageFile(path);
        if (file == null) {
            return null;
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            return null;
        }
    }

    /** Approved tree raster under {@code data/images/trees/tree_&lt;id&gt;.img}. */
    public static byte[] loadTreeImageById(int id) {
        if (id <= 0) {
            return null;
        }
        ensureDirectories();
        File f = new File(TREES_DIR, "tree_" + id + ".img");
        if (!f.exists()) {
            return null;
        }
        try {
            return Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            return null;
        }
    }

    /** Pending submission raster under {@code data/images/pending/pending_&lt;id&gt;.img}. */
    public static byte[] loadPendingImageById(int id) {
        if (id <= 0) {
            return null;
        }
        ensureDirectories();
        File f = new File(PENDING_DIR, "pending_" + id + ".img");
        if (!f.exists()) {
            return null;
        }
        try {
            return Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            return null;
        }
    }

    public static String moveToTree(String currentPath, int id) {
        return moveImage(currentPath, TREES_DIR, "tree_" + id + ".img");
    }

    public static String moveToDeleted(String currentPath, int id) {
        return moveImage(currentPath, DELETED_DIR, "deleted_" + id + ".img");
    }

    /** Stable path stored in JSON (forward slashes, works on any OS working directory). */
    public static String portableTreeImagePath(int id) {
        if (id <= 0) {
            return "";
        }
        return "data/images/trees/tree_" + id + ".img";
    }

    public static String portablePendingImagePath(int id) {
        if (id <= 0) {
            return "";
        }
        return "data/images/pending/pending_" + id + ".img";
    }

    public static String portableDeletedImagePath(int id) {
        if (id <= 0) {
            return "";
        }
        return "data/images/deleted/deleted_" + id + ".img";
    }

    public static boolean treeImageFileExists(int id) {
        if (id <= 0) {
            return false;
        }
        return new File(TREES_DIR, "tree_" + id + ".img").isFile();
    }

    public static boolean deletedImageFileExists(int id) {
        if (id <= 0) {
            return false;
        }
        return new File(DELETED_DIR, "deleted_" + id + ".img").isFile();
    }

    public static byte[] loadDeletedImageById(int id) {
        if (id <= 0) {
            return null;
        }
        ensureDirectories();
        File f = new File(DELETED_DIR, "deleted_" + id + ".img");
        if (!f.exists()) {
            return null;
        }
        try {
            return Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            return null;
        }
    }

    /** Collapses stored paths to {@code data/images/...} with forward slashes for JSON. */
    public static String toPortableDataImagePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String n = path.replace('\\', '/');
        int idx = n.indexOf("data/images/");
        if (idx >= 0) {
            return n.substring(idx);
        }
        return n;
    }

    public static void deleteImage(String path) {
        File file = resolveExistingImageFile(path);
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private static String moveImage(String currentPath, File targetDir, String targetName) {
        if (currentPath == null || currentPath.isEmpty()) {
            return null;
        }
        File source = resolveExistingImageFile(currentPath);
        if (source == null || !source.exists()) {
            return null;
        }
        ensureDirectories();
        File destination = new File(targetDir, targetName);
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return destination.getPath();
        } catch (IOException e) {
            throw new RuntimeException("Unable to move image " + source + " → " + destination, e);
        }
    }
}
