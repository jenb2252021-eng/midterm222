package Model;

import java.io.Serial;
import java.io.Serializable;

/**
 * This class stores and acts as a blueprint for the Trees.
 */
public class Tree implements Serializable {
    @Serial
    private static final long serialVersionUID = 3L; // incremented version
    public int id;
    public String species;
    public String location;
    public double height;
    public double diameter;
    public String plantedDate;
    public String status;
    public String notes;
    public String imagePath;
    public byte[] imageBytes;
    // Username of the account that created this tree
    public String ownerUsername;

    public Tree(int id, String species, String location,
                double height, double diameter,
                String plantedDate, String status, String notes,
                byte[] imageBytes, String ownerUsername) { // Added ownerUsername to constructor

        this.id = id;
        this.species = species;
        this.location = location;
        this.height = height;
        this.diameter = diameter;
        this.plantedDate = plantedDate;
        this.status = status;
        this.notes = notes;
        this.imageBytes = imageBytes;
        this.imagePath = null;
        this.ownerUsername = ownerUsername;
    }
}
