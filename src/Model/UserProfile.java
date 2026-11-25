package Model;

import java.io.Serializable;

/**
 * Lightweight user profile exposed to clients over RMI.
 * Does not include password.
 */
public class UserProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    public String username;
    public String displayName;
    public byte[] avatarBytes;

    public UserProfile() {
    }

    public UserProfile(String username, String displayName, byte[] avatarBytes) {
        this.username = username;
        this.displayName = displayName;
        this.avatarBytes = avatarBytes;
    }
}

