package JSONUtil;

import Model.Tree;
import storage.IdGenerator;
import storage.ImageStorage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import storage.AppDataPaths;

public class DeletedTreeJSONUtil {
    private static final File FILE = AppDataPaths.dataFile("deleted_tree_data.json");
    private static final ArrayList<Tree> deletedTrees = new ArrayList<>();
    private static final IdGenerator ID_GENERATOR = IdGenerator.getInstance();

    static {
        loadFromJson();
    }

    public static synchronized void addDeletedTree(Tree t) {
        ID_GENERATOR.syncWithId(t.id);
        ensureDeletedImage(t);
        deletedTrees.add(t);
        persist();
    }

    /** Removes an archived record (restore or permanent delete) and returns it, or null when not archived. */
    public static synchronized Tree removeDeletedTree(int id) {
        for (int i = 0; i < deletedTrees.size(); i++) {
            Tree t = deletedTrees.get(i);
            if (t.id == id) {
                if (t.imageBytes == null || t.imageBytes.length == 0) t.imageBytes = ImageStorage.loadDeletedImageById(id);
                deletedTrees.remove(i);
                persist();
                return t;
            }
        }
        return null;
    }

    public static synchronized ArrayList<Tree> getDeletedTrees() {
        return new ArrayList<>(deletedTrees);
    }

    private static void loadFromJson() {
        try {
            DeletedTreeDataRoot root = JacksonTreeIO.read(FILE, DeletedTreeDataRoot.class);
            if (root.deletedTrees == null) {
                return;
            }
            for (TreePersistenceRecord rec : root.deletedTrees) {
                Tree t = rec.toTree();
                deletedTrees.add(t);
                ID_GENERATOR.syncWithId(t.id);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void persist() {
        DeletedTreeDataRoot root = new DeletedTreeDataRoot();
        for (Tree t : deletedTrees) {
            root.deletedTrees.add(TreePersistenceRecord.fromTree(t));
        }
        try {
            JacksonTreeIO.write(FILE, root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist deleted tree data", e);
        }
    }

    private static void ensureDeletedImage(Tree tree) {
        if (tree.imageBytes != null && tree.imageBytes.length > 0) {
            ImageStorage.storeDeletedImage(tree.id, tree.imageBytes);
            tree.imagePath = ImageStorage.portableDeletedImagePath(tree.id);
            return;
        }
        if (tree.id > 0) {
            byte[] fromTrees = ImageStorage.loadTreeImageById(tree.id);
            if (fromTrees != null && fromTrees.length > 0) {
                tree.imageBytes = fromTrees;
                ImageStorage.storeDeletedImage(tree.id, fromTrees);
                tree.imagePath = ImageStorage.portableDeletedImagePath(tree.id);
                return;
            }
        }
        if (tree.imagePath != null && !tree.imagePath.trim().isEmpty()) {
            String moved = ImageStorage.moveToDeleted(tree.imagePath, tree.id);
            if (moved != null) {
                tree.imagePath = ImageStorage.portableDeletedImagePath(tree.id);
                tree.imageBytes = ImageStorage.loadDeletedImageById(tree.id);
            } else {
                tree.imageBytes = ImageStorage.loadImage(tree.imagePath);
                if (tree.imageBytes != null && tree.imageBytes.length > 0) {
                    ImageStorage.storeDeletedImage(tree.id, tree.imageBytes);
                    tree.imagePath = ImageStorage.portableDeletedImagePath(tree.id);
                }
            }
        }
    }
}
