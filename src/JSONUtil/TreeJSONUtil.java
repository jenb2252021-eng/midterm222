package JSONUtil;

import Model.Tree;
import storage.ImageStorage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import storage.AppDataPaths;
import storage.IdGenerator;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class TreeJSONUtil {
    private static final File FILE = AppDataPaths.dataFile("tree_data.json");
    private static final ArrayList<Tree> trees = new ArrayList<>();
    private static final IdGenerator ID_GENERATOR = IdGenerator.getInstance();
    private static final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    static {
        loadFromJson();
    }

    public static Tree getTreeById(int id) {
        readWriteLock.readLock().lock();
        try {
            for (Tree t : trees) {
                if (t.id == id) {
                    return t;
                }
            }
            return null;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public static String addTree(Tree t) {
        readWriteLock.writeLock().lock();
        try {
        String valid = validate(t);
        if (!"OK".equals(valid)) return valid;
        if (t.id <= 0) {
            t.id = ID_GENERATOR.nextId();
        } else {
            ID_GENERATOR.syncWithId(t.id);
        }
        ensureTreeImage(t);
        trees.add(t);
        persist();
        return "SUCCESS: Tree added with ID " + t.id;
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public static String addTreeWithId(Tree t) {
        readWriteLock.writeLock().lock();
        try {
        String valid = validate(t);
        if (!"OK".equals(valid)) return valid;
        if (t.id <= 0) {
            t.id = ID_GENERATOR.nextId();
        } else {
            ID_GENERATOR.syncWithId(t.id);
        }
        ensureTreeImage(t);
        trees.add(t);
        persist();
        return "SUCCESS: Tree added with ID " + t.id;
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public static ArrayList<Tree> getAllTrees() {
        readWriteLock.readLock().lock();
        try {
        ArrayList<Tree> copy = new ArrayList<>();
        for (Tree t : trees) {
            copy.add(t);
        }
        return copy;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Ensures {@link Tree#imageBytes} are loaded from disk before RMI sends the object to the client.
     */
    public static void hydrateTreeImageForClient(Tree t) {
        if (t == null) {
            return;
        }
        if (t.imageBytes != null && t.imageBytes.length > 0) {
            return;
        }
        if (t.imagePath != null && !t.imagePath.trim().isEmpty()) {
            t.imageBytes = ImageStorage.loadImage(t.imagePath);
        }
        if (t.imageBytes == null || t.imageBytes.length == 0) {
            t.imageBytes = ImageStorage.loadTreeImageById(t.id);
        }
    }

    public static String updateTree(Tree t) {
        readWriteLock.writeLock().lock();
        try {
        String valid = validate(t);
        if (!"OK".equals(valid)) return valid;
        for (int i = 0; i < trees.size(); i++) {
            if (trees.get(i).id == t.id) {
                Tree existing = trees.get(i);
                ensureTreeImage(t);
                if (t.imageBytes == null && existing.imageBytes != null && t.imagePath == null) {
                    t.imageBytes = existing.imageBytes;
                    t.imagePath = existing.imagePath;
                }
                trees.set(i, t);
                persist();
                return "SUCCESS: Tree updated";
            }
        }
        return "ERROR: Tree ID not found";
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public static String deleteTree(int id) {
        readWriteLock.writeLock().lock();
        try {
        for (int i = 0; i < trees.size(); i++) {
            if (trees.get(i).id == id) {
                trees.remove(i);
                persist();
                return "SUCCESS: Tree deleted";
            }
        }
        return "ERROR: Tree ID not found";
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public static ArrayList<Tree> searchBySpecies(String species) {
        readWriteLock.readLock().lock();
        try {
        ArrayList<Tree> result = new ArrayList<>();
        for (Tree t : trees) {
            if (t.species != null && t.species.equalsIgnoreCase(species)) {
                result.add(t);
            }
        }
        return result;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public static ArrayList<Tree> searchByStatus(String status) {
        readWriteLock.readLock().lock();
        try {
        ArrayList<Tree> result = new ArrayList<>();
        for (Tree t : trees) {
            if (t.status != null && t.status.equalsIgnoreCase(status)) {
                result.add(t);
            }
        }
        return result;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public static boolean isValidStatus(String status) {
        return status != null && (status.equalsIgnoreCase("Healthy")
                || status.equalsIgnoreCase("Monitoring")
                || status.equalsIgnoreCase("At Risk")
                || status.equalsIgnoreCase("Destroyed"));
    }

    private static String validate(Tree t) {
        if (t.species == null || t.species.trim().isEmpty()) return "ERROR: Species required";
        if (t.location == null || t.location.trim().isEmpty()) return "ERROR: Location required";
        if (!Double.isFinite(t.height) || t.height <= 0) return "ERROR: Height must be positive";
        if (!Double.isFinite(t.diameter) || t.diameter <= 0) return "ERROR: Diameter must be positive";
        if (t.plantedDate == null || !t.plantedDate.matches("\\d{4}-\\d{2}-\\d{2}"))
            return "ERROR: Planted Date must be YYYY-MM-DD";
        try { java.time.LocalDate.parse(t.plantedDate); }
        catch (java.time.DateTimeException ex) { return "ERROR: Invalid planting date"; }
        if (client_GUI.GeoCoordFormat.tryParse(t.location) == null) return "ERROR: Pin a valid map location";
        if (!isValidStatus(t.status)) return "ERROR: Choose a valid status";
        return "OK";
    }

    public static String validateForPending(Tree t) {
        return validate(t);
    }

    private static void loadFromJson() {
        try {
            TreeDataRoot root = JacksonTreeIO.read(FILE, TreeDataRoot.class);
            if (root.trees == null) {
                return;
            }
            for (TreePersistenceRecord rec : root.trees) {
                Tree t = rec.toTree();
                trees.add(t);
                ID_GENERATOR.syncWithId(t.id);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void persist() {
        TreeDataRoot root = new TreeDataRoot();
        for (Tree t : trees) {
            root.trees.add(TreePersistenceRecord.fromTree(t));
        }
        try {
            JacksonTreeIO.write(FILE, root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist tree data", e);
        }
    }

    private static void ensureTreeImage(Tree tree) {
        if (tree == null || tree.id <= 0) {
            return;
        }
        if (tree.imageBytes != null && tree.imageBytes.length > 0) {
            ImageStorage.storeTreeImage(tree.id, tree.imageBytes);
            tree.imagePath = ImageStorage.portableTreeImagePath(tree.id);
            return;
        }
        if (tree.imagePath != null && tree.imagePath.contains("pending_")) {
            String moved = ImageStorage.moveToTree(tree.imagePath, tree.id);
            if (moved != null) {
                tree.imagePath = ImageStorage.portableTreeImagePath(tree.id);
                tree.imageBytes = ImageStorage.loadTreeImageById(tree.id);
                return;
            }
            byte[] pendingBytes = ImageStorage.loadImage(tree.imagePath);
            if (pendingBytes == null || pendingBytes.length == 0) {
                pendingBytes = ImageStorage.loadPendingImageById(tree.id);
            }
            if (pendingBytes != null && pendingBytes.length > 0) {
                ImageStorage.storeTreeImage(tree.id, pendingBytes);
                tree.imagePath = ImageStorage.portableTreeImagePath(tree.id);
                tree.imageBytes = pendingBytes;
                return;
            }
        }
        if (tree.imagePath != null && !tree.imagePath.trim().isEmpty()) {
            tree.imageBytes = ImageStorage.loadImage(tree.imagePath);
        }
        if (tree.imageBytes == null || tree.imageBytes.length == 0) {
            tree.imageBytes = ImageStorage.loadTreeImageById(tree.id);
        }
        if (tree.imageBytes != null && tree.imageBytes.length > 0) {
            tree.imagePath = ImageStorage.portableTreeImagePath(tree.id);
        }
    }
}
