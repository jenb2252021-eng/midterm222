package Model;

import java.io.Serializable;

/**
 * The class stores the data for the Users login information.
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    public String username;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public String password; // Legacy records only; cleared during migration.
    public String passwordHash;
    /**
     * Human-friendly display name shown in chat and tree listings.
     */
    public String displayName;
    /**
     * Optional path to the avatar image on disk.
     */
    public String avatarPath;

    public User() {
    }

    public User(String username, String password){
        this(username, password, username, null);
    }

    public User(String username, String password, String displayName, String avatarPath){
        this.username = username;
        this.password = password;
        this.displayName = (displayName == null || displayName.trim().isEmpty()) ? username : displayName.trim();
        this.avatarPath = avatarPath;
    }
}
