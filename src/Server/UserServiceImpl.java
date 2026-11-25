package Server;

import JSONUtil.ActivityLogJSONUtil;
import JSONUtil.UserJSONUtil;
import Model.UserProfile;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.rmi.server.UnicastRemoteObject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Server-side implementation of UserService.
 * Handles authentication and user management.
 */
public class UserServiceImpl extends UnicastRemoteObject implements UserService {
    private static final long serialVersionUID = 1L;
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(5);
    private final ConcurrentMap<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    public UserServiceImpl() throws RemoteException {
        super(1101);
    }

    private static String getClientHostSafe() {
        try {
            return RemoteServer.getClientHost();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    @Override
    public String authenticate(String username, String password) throws RemoteException {
        if (username == null || password == null) {
            return "ERROR: Username and password are required.";
        }

        LoginAttempt attempt = loginAttempts.computeIfAbsent(username, key -> new LoginAttempt());
        if (attempt.isLocked()) {
            return "ERROR: Maximum login attempts reached. Please wait " +
                    formatDuration(attempt.remaining()) +
                    " before trying again.";
        }

        if (!UserJSONUtil.authenticate(username, password)) {
            attempt.recordFailure();
            if (attempt.isLocked()) {
                return "ERROR: Maximum login attempts reached. Please wait " +
                        formatDuration(attempt.remaining()) +
                        " before trying again.";
            }
            return "ERROR: Invalid credentials. Attempts left: " + attempt.attemptsLeft();
        }

        attempt.reset();
        loginAttempts.remove(username);

        // Check if server is enabled for non-admin users
        if (!TreeServer.isServerEnabled() && !"admin".equals(username)) {
            return "ERROR: Server is currently disabled. Only admin can log in.";
        }

        // Check if user already logged in
        String clientHost = getClientHostSafe();
        String token = SessionManager.open(username, clientHost);
        if (token == null) {
            return "ERROR: This account is already logged in on another device.";
        }

        ActivityLogJSONUtil.log(username, "LOGIN", "Signed in");
        return "SUCCESS: " + token;
    }

    @Override
    public String register(String username, String password) throws RemoteException {
        String result = UserJSONUtil.register(username, password);
        if (result.startsWith("SUCCESS")) ActivityLogJSONUtil.log(username, "REGISTER", "Ranger account created");
        return result;
    }

    @Override
    public String resetPassword(String username, String oldPassword, String newPassword) throws RemoteException {
        String result = UserJSONUtil.resetPassword(username, oldPassword, newPassword);
        if (result.startsWith("SUCCESS")) ActivityLogJSONUtil.log(username, "PASSWORD_CHANGE", "Password updated");
        return result;
    }

    @Override
    public String adminSetPassword(String sessionToken, String targetUsername, String newPassword) throws RemoteException {
        String requesterUsername = SessionManager.identity(sessionToken);
        if (!"admin".equals(requesterUsername)) {
            return "ERROR: Access Denied. Admins only.";
        }

        String result = UserJSONUtil.adminSetPassword(targetUsername, newPassword);
        if (result.startsWith("SUCCESS")) {
            ActivityLogJSONUtil.log("admin", "ADMIN_CHANGE_PASSWORD", "Password changed for user: " + targetUsername);
        }
        return result;
    }

    @Override
    public String deleteUser(String sessionToken, String targetUsername) throws RemoteException {
        String requesterUsername = SessionManager.identity(sessionToken);
        if (!"admin".equals(requesterUsername)) {
            return "ERROR: Access Denied. Admins only.";
        }

        String result = UserJSONUtil.deleteUser(targetUsername);
        if (result.startsWith("SUCCESS")) {
            SessionManager.deactivateUser(targetUsername);
            ActivityLogJSONUtil.log("admin", "ADMIN_DELETE_ACCOUNT", "Deleted user account: " + targetUsername);
        }
        return result;
    }

    @Override
    public String updatePassword(String sessionToken, String targetUsername, String oldPassword, String newPassword) throws RemoteException {
        String requesterUsername = SessionManager.identity(sessionToken);
        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            return "ERROR: target username is required.";
        }
        String requester = requesterUsername == null ? "" : requesterUsername.trim();
        String target = targetUsername.trim();

        // Admin can update any password (oldPassword is optional/ignored).
        if ("admin".equals(requester)) {
            return adminSetPassword(sessionToken, target, newPassword);
        }

        // Non-admin: caller must be updating their own password.
        if (!target.equals(requester)) {
            return "ERROR: Access Denied. You can only update your own password.";
        }

        return resetPassword(target, oldPassword, newPassword);
    }

    @Override
    public String signOut(String sessionToken) throws RemoteException {
        String username = SessionManager.identity(sessionToken);
        if (username == null || username.trim().isEmpty()) {
            return "ERROR: Username is required to log out.";
        }
        SessionManager.deactivateUser(username);
        ActivityLogJSONUtil.log(username, "LOGOUT", "User signed out");
        return "SUCCESS: Logged out";
    }

    @Override
    public ArrayList<String> getAllUsernames(String sessionToken) throws RemoteException {
        String requesterUsername = SessionManager.identity(sessionToken);
        if (!"admin".equals(requesterUsername)) {
            throw new RemoteException("ERROR: Access Denied. Admins only.");
        }
        return UserJSONUtil.getAllUsernames();
    }

    @Override
    public String updateProfile(String sessionToken, String newDisplayName, byte[] avatarBytes) throws RemoteException {
        String requesterUsername = SessionManager.identity(sessionToken);
        if (requesterUsername == null || requesterUsername.trim().isEmpty()) {
            return "ERROR: Username is required.";
        }
        String result = UserJSONUtil.updateProfile(requesterUsername, newDisplayName, avatarBytes);
        if (result.startsWith("SUCCESS")) {
            ActivityLogJSONUtil.log(requesterUsername, "PROFILE_UPDATE", "Updated display name/avatar");
        }
        return result;
    }

    @Override
    public UserProfile getProfile(String sessionToken, String targetUsername) throws RemoteException {
        String requesterUsername = SessionManager.identity(sessionToken);
        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            throw new RemoteException("ERROR: target username is required.");
        }
        UserProfile profile = UserJSONUtil.getProfile(targetUsername);
        if (profile == null) {
            throw new RemoteException("ERROR: User not found: " + targetUsername);
        }
        return profile;
    }

    private static String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        long minutes = seconds / 60;
        long secs = seconds % 60;
        if (minutes > 0) {
            return String.format("%d minute%s %d second%s", minutes, minutes == 1 ? "" : "s", secs, secs == 1 ? "" : "s");
        }
        return secs + " second" + (secs == 1 ? "" : "s");
    }

    private static final class LoginAttempt {
        private int failures = 0;
        private Instant lockUntil = Instant.EPOCH;

        boolean isLocked() {
            return lockUntil.isAfter(Instant.now());
        }

        Duration remaining() {
            return isLocked() ? Duration.between(Instant.now(), lockUntil) : Duration.ZERO;
        }

        void recordFailure() {
            failures++;
            if (failures >= MAX_ATTEMPTS) {
                lockUntil = Instant.now().plus(LOCK_DURATION);
            }
        }

        void reset() {
            failures = 0;
            lockUntil = Instant.EPOCH;
        }

        int attemptsLeft() {
            return Math.max(0, MAX_ATTEMPTS - failures);
        }
    }
}
