package JSONUtil;

import Model.Tree;
import storage.ImageStorage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import storage.AppDataPaths;
import storage.IdGenerator;

public class PendingTreeJSONUtil {
    private static final File FILE = AppDataPaths.dataFile("pending_tree_data.json");
    private static final ArrayList<Tree> pendingTrees = new ArrayList<>();
    private static final IdGenerator ID_GENERATOR = IdGenerator.getInstance();

    static {
        loadFromJson();
    }

    public static synchronized ArrayList<Tree> getAllPending() {
        return new ArrayList<>(pendingTrees);
    }

    /** Load pending image bytes from disk for RMI clients. */
    public static void hydratePendingImageForClient(Tree t) {
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
            t.imageBytes = ImageStorage.loadPendingImageById(t.id);
        }
    }

    public static synchronized Tree getPendingById(int id) {
        for (Tree t : pendingTrees) {
            if (t.id == id) return t;
        }
        return null;
    }

    public static synchronized String addPendingTree(Tree t) {
        String valid = TreeJSONUtil.validateForPending(t);
        if (!"OK".equals(valid)) return valid;
        if (t.id <= 0) {
            t.id = ID_GENERATOR.nextId();
        } else {
            ID_GENERATOR.syncWithId(t.id);
        }
        ensurePendingImage(t);
        pendingTrees.add(t);
        persist();
        return "SUCCESS_PENDING: Tree submitted for admin approval (Pending ID " + t.id + ")";
    }

    public static synchronized String removePending(int id) {
        for (int i = 0; i < pendingTrees.size(); i++) {
            if (pendingTrees.get(i).id == id) {
                Tree removed = pendingTrees.remove(i);
                // After approval, imagePath points at trees/ — never delete that. Only drop pending rasters.
                if (removed.imagePath != null && ImageStorage.isPendingStoredPath(removed.imagePath)) {
                    ImageStorage.deleteImage(removed.imagePath);
                }
                persist();
                return "SUCCESS: Pending tree removed";
            }
        }
        return "ERROR: Pending tree ID not found";
    }

    private static void loadFromJson() {
        try {
            PendingTreeDataRoot root = JacksonTreeIO.read(FILE, PendingTreeDataRoot.class);
            if (root.pendingTrees == null) {
                return;
            }
            for (TreePersistenceRecord rec : root.pendingTrees) {
                Tree t = rec.toTree();
                pendingTrees.add(t);
                ID_GENERATOR.syncWithId(t.id);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void persist() {
        PendingTreeDataRoot root = new PendingTreeDataRoot();
        for (Tree t : pendingTrees) {
            root.pendingTrees.add(TreePersistenceRecord.fromTree(t));
        }
        try {
            JacksonTreeIO.write(FILE, root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist pending tree data", e);
        }
    }

    private static void ensurePendingImage(Tree tree) {
        if (tree.imageBytes != null && tree.imageBytes.length > 0) {
            tree.imagePath = ImageStorage.storePendingImage(tree.id, tree.imageBytes);
            return;
        }
        if (tree.imagePath != null && !tree.imagePath.trim().isEmpty()) {
            tree.imageBytes = ImageStorage.loadImage(tree.imagePath);
        }
    }
}
