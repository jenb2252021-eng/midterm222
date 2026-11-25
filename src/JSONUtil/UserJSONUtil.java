package JSONUtil;

import Model.User;
import Model.UserProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import storage.ImageStorage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import storage.AppDataPaths;

public class UserJSONUtil {
    private static final File FILE = AppDataPaths.dataFile("users.json");
    private static final ArrayList<User> users = new ArrayList<>();
    private static final ObjectMapper MAPPER = JacksonTreeIO.mapper();

    static {
        loadFromJson();
        boolean migrated = false;
        for (User user : users) {
            if (user.password != null) {
                user.passwordHash = Passwords.hash(user.password);
                user.password = null;
                migrated = true;
            }
        }
        if (migrated) saveToJson();
    }

    public static synchronized boolean needsAdministrator() {
        return users.isEmpty() && !FILE.exists();
    }

    public static synchronized boolean createLocalAdministrator(String password) {
        if (!needsAdministrator() || password == null || password.length() < 8) return false;
        User administrator = new User("admin", null, "Administrator", null);
        administrator.passwordHash = Passwords.hash(password);
        users.add(administrator);
        saveToJson();
        return true;
    }

    public static synchronized boolean authenticate(String username, String password) {
        for (User u : users) {
            if (u.username.equals(username) && Passwords.matches(password, u.passwordHash)) return true;
        }
        return false;
    }

    public static synchronized String saveToJson() {
        UsersFile doc = new UsersFile();
        doc.users = new ArrayList<>(users);
        try {
            Files.createDirectories(FILE.toPath().getParent());
            java.nio.file.Path temporary = Files.createTempFile(FILE.toPath().getParent(), "users-", ".tmp");
            try {
                MAPPER.writeValue(temporary.toFile(), doc);
                Files.move(temporary, FILE.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist users", e);
        }
        return "SUCCESS";
    }

    public static synchronized String register(String username, String password) {
        String u = (username == null) ? "" : username.trim();
        String p = (password == null) ? "" : password.trim();

        if (users.isEmpty() || "admin".equalsIgnoreCase(u)) return "ERROR: Administrator setup is available on the server laptop.";
        if (u.isEmpty() || p.isEmpty()) {
            return "ERROR: Username and password cannot be empty.";
        }
        if (u.length() < 3) {
            return "ERROR: Username must be at least 3 characters.";
        }
        if (p.length() < 4) {
            return "ERROR: Password must be at least 4 characters.";
        }
        for (User existing : users) {
            if (existing.username.equalsIgnoreCase(u)) {
                return "ERROR: Username already exists.";
            }
        }
        User account = new User(u, null, u, null);
        account.passwordHash = Passwords.hash(p);
        users.add(account);
        saveToJson();
        return "SUCCESS: Registration complete.";
    }

    public static synchronized String resetPassword(String username, String oldPassword, String newPassword) {
        String u = (username == null) ? "" : username.trim();
        String oldPw = (oldPassword == null) ? "" : oldPassword.trim();
        String newPw = (newPassword == null) ? "" : newPassword.trim();

        if (u.isEmpty() || oldPw.isEmpty() || newPw.isEmpty()) {
            return "ERROR: Username, old password and new password are required.";
        }
        if (newPw.length() < 4) {
            return "ERROR: New password must be at least 4 characters.";
        }
        for (User user : users) {
            if (user.username.equals(u)) {
                if (!Passwords.matches(oldPw, user.passwordHash)) {
                    return "ERROR: Old password is incorrect.";
                }
                user.passwordHash = Passwords.hash(newPw);
                saveToJson();
                return "SUCCESS: Password has been updated.";
            }
        }
        return "ERROR: Username not found.";
    }

    public static synchronized String adminSetPassword(String targetUsername, String newPassword) {
        String u = (targetUsername == null) ? "" : targetUsername.trim();
        String p = (newPassword == null) ? "" : newPassword.trim();
        if (u.isEmpty() || p.isEmpty()) {
            return "ERROR: Username and new password are required.";
        }
        if (p.length() < 4) {
            return "ERROR: Password must be at least 4 characters.";
        }
        for (User user : users) {
            if (user.username.equals(u)) {
                user.passwordHash = Passwords.hash(p);
                saveToJson();
                return "SUCCESS: Password updated for user '" + u + "'.";
            }
        }
        return "ERROR: Username not found.";
    }

    public static synchronized String deleteUser(String targetUsername) {
        String u = (targetUsername == null) ? "" : targetUsername.trim();
        if (u.isEmpty()) {
            return "ERROR: Username required.";
        }
        if ("admin".equalsIgnoreCase(u)) {
            return "ERROR: The primary admin account cannot be deleted.";
        }
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).username.equals(u)) {
                users.remove(i);
                saveToJson();
                return "SUCCESS: User '" + u + "' has been deleted.";
            }
        }
        return "ERROR: Username not found.";
    }

    public static synchronized ArrayList<String> getAllUsernames() {
        ArrayList<String> names = new ArrayList<>();
        for (User u : users) {
            names.add(u.username);
        }
        return names;
    }

    private static void loadFromJson() {
        if (!FILE.exists()) return;
        try {
            String raw = new String(Files.readAllBytes(FILE.toPath()), StandardCharsets.UTF_8);
            if (raw.trim().isEmpty()) return;
            JsonNode root = MAPPER.readTree(raw);
            ArrayNode arr = JacksonTreeIO.asArray(root.get("users"), MAPPER);
            for (JsonNode n : arr) {
                User user = MAPPER.treeToValue(n, User.class);
                if (user.username == null || user.username.isEmpty()) continue;
                if (user.displayName == null || user.displayName.trim().isEmpty()) {
                    user.displayName = user.username;
                }
                if (user.avatarPath != null && user.avatarPath.trim().isEmpty()) {
                    user.avatarPath = null;
                }
                users.add(user);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read existing user accounts", e);
        }
    }

    public static synchronized String updateProfile(String username, String newDisplayName, byte[] avatarBytes) {
        String u = (username == null) ? "" : username.trim();
        if (u.isEmpty()) {
            return "ERROR: Username is required.";
        }
        String name = (newDisplayName == null) ? "" : newDisplayName.trim();
        for (User user : users) {
            if (user.username.equals(u)) {
                if (!name.isEmpty()) {
                    user.displayName = name;
                }
                if (avatarBytes != null && avatarBytes.length > 0) {
                    String path = ImageStorage.storeAvatar(u, avatarBytes);
                    user.avatarPath = path;
                }
                saveToJson();
                return "SUCCESS: Profile updated.";
            }
        }
        return "ERROR: Username not found.";
    }

    public static synchronized UserProfile getProfile(String targetUsername) {
        if (targetUsername == null) return null;
        String u = targetUsername.trim();
        if (u.isEmpty()) return null;
        for (User user : users) {
            if (user.username.equals(u)) {
                byte[] avatar = null;
                if (user.avatarPath != null && !user.avatarPath.isEmpty()) {
                    avatar = ImageStorage.loadImage(user.avatarPath);
                }
                return new UserProfile(user.username,
                        user.displayName != null ? user.displayName : user.username,
                        avatar);
            }
        }
        return null;
    }

    private static class UsersFile {
        public List<User> users;
    }
}
