package JSONUtil;

import Model.Tree;
import storage.ImageStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON shape for tree rows on disk (field "owner" maps to {@link Tree#ownerUsername}).
 */
public class TreePersistenceRecord {
    public int id;
    public String species = "";
    public String location = "";
    public double height;
    public double diameter;
    public String plantedDate = "";
    public String status = "";
    public String notes = "";
    public String owner = "";
    public String imagePath = "";

    public static TreePersistenceRecord fromTree(Tree t) {
        TreePersistenceRecord r = new TreePersistenceRecord();
        r.id = t.id;
        r.species = n(t.species);
        r.location = n(t.location);
        r.height = t.height;
        r.diameter = t.diameter;
        r.plantedDate = n(t.plantedDate);
        r.status = n(t.status);
        r.notes = n(t.notes);
        r.owner = n(t.ownerUsername);
        r.imagePath = resolveImagePathForPersist(t);
        return r;
    }

    public Tree toTree() {
        String path = (imagePath == null || imagePath.trim().isEmpty()) ? null : imagePath.trim();
        byte[] bytes = path != null ? ImageStorage.loadImage(path) : null;
        if ((bytes == null || bytes.length == 0) && id > 0) {
            bytes = ImageStorage.loadTreeImageById(id);
        }
        if ((bytes == null || bytes.length == 0) && id > 0) {
            bytes = ImageStorage.loadDeletedImageById(id);
        }
        Tree tree = new Tree(id, n(species), n(location), height, diameter,
                n(plantedDate), n(status), n(notes), bytes, n(owner));
        if (bytes != null && bytes.length > 0 && id > 0) {
            if (ImageStorage.treeImageFileExists(id)) {
                tree.imagePath = ImageStorage.portableTreeImagePath(id);
            } else if (ImageStorage.deletedImageFileExists(id)) {
                tree.imagePath = ImageStorage.portableDeletedImagePath(id);
            } else {
                tree.imagePath = path != null ? ImageStorage.toPortableDataImagePath(path) : "";
            }
        } else if (path != null) {
            tree.imagePath = ImageStorage.toPortableDataImagePath(path);
        } else {
            tree.imagePath = "";
        }
        return tree;
    }

    public static List<TreePersistenceRecord> listFromTrees(List<Tree> trees) {
        List<TreePersistenceRecord> out = new ArrayList<>(trees.size());
        for (Tree t : trees) {
            out.add(fromTree(t));
        }
        return out;
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }

    private static boolean pathLooksDeleted(String p) {
        if (p == null || p.isEmpty()) {
            return false;
        }
        return p.replace('\\', '/').contains("images/deleted");
    }

    /**
     * Writes stable {@code data/images/...} paths into JSON and ensures raster files exist when bytes are present.
     */
    private static String resolveImagePathForPersist(Tree t) {
        if (t == null || t.id <= 0) {
            return "";
        }
        boolean deletedSide = pathLooksDeleted(t.imagePath);

        if (t.imageBytes != null && t.imageBytes.length > 0) {
            if (deletedSide) {
                ImageStorage.storeDeletedImage(t.id, t.imageBytes);
                return ImageStorage.portableDeletedImagePath(t.id);
            }
            ImageStorage.storeTreeImage(t.id, t.imageBytes);
            return ImageStorage.portableTreeImagePath(t.id);
        }
        if (ImageStorage.treeImageFileExists(t.id)) {
            return ImageStorage.portableTreeImagePath(t.id);
        }
        if (ImageStorage.deletedImageFileExists(t.id)) {
            return ImageStorage.portableDeletedImagePath(t.id);
        }
        if (t.imagePath != null && !t.imagePath.trim().isEmpty()) {
            return ImageStorage.toPortableDataImagePath(t.imagePath.trim());
        }
        return "";
    }
}
