package Server;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages active user sessions to prevent multiple concurrent logins
 * from the same account.
 */
public class SessionManager {
    // usernameKey -> clientHost (best-effort). Tracking host lets us allow the same device to re-login
    // if the previous client crashed or logout didn't reach the server.
    private static final ConcurrentMap<String, String> activeUserHost = new ConcurrentHashMap<>();

    private static final ConcurrentMap<String, String> tokens = new ConcurrentHashMap<>();

    static synchronized void clearSessions() {
        tokens.clear();
        activeUserHost.clear();
    }

    static synchronized String open(String username, String host) {
        if (!activateUser(username, host)) return null;
        tokens.values().removeIf(username::equals);
        String token = java.util.UUID.randomUUID().toString();
        tokens.put(token, username);
        return token;
    }

    static String identity(String token) throws java.rmi.RemoteException {
        String username = token == null ? null : tokens.get(token);
        if (username == null) throw new java.rmi.RemoteException("Session expired. Please sign in again.");
        return username;
    }

    private static String key(String username) {
        return username == null ? null : username.trim().toLowerCase();
    }

    /**
     * Check if a user is already logged in.
     * @param username The username to check
     * @return true if user is logged in
     */
    public static boolean isUserActive(String username) {
        String k = key(username);
        return k != null && activeUserHost.containsKey(k);
    }

    /**
     * Mark a user as logged in.
     * @param username The username to activate
     * @return true if successfully activated, false if already active on a different device
     */
    public static boolean activateUser(String username) {
        return activateUser(username, "unknown");
    }

    /**
     * Mark a user as logged in with an associated client host identifier.
     * If the same user logs in again from the same host, allow it (idempotent).
     * @return true if activated or same-host re-login; false if active on a different host.
     */
    public static boolean activateUser(String username, String clientHost) {
        String k = key(username);
        if (k == null || k.isEmpty()) return false;
        String host = (clientHost == null) ? "unknown" : clientHost.trim();

        String existing = activeUserHost.putIfAbsent(k, host);
        if (existing == null) {
            return true;
        }
        // Allow same-device re-login (prevents "stuck logged in" after logout/app crash on same PC).
        return existing.equals(host);
    }

    /**
     * Mark a user as logged out.
     * @param username The username to deactivate
     */
    public static synchronized void deactivateUser(String username) {
        String k = key(username);
        if (k != null) {
            activeUserHost.remove(k);
            tokens.values().removeIf(username::equalsIgnoreCase);
        }
    }

    /**
     * Get all active usernames.
     * @return Copy of the active users set
     */
    public static Set<String> getActiveUsers() {
        return Set.copyOf(activeUserHost.keySet());
    }
}
