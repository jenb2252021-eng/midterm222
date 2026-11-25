package Server;

import Model.UserProfile;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

/**
 * Remote interface for user authentication and management.
 */
public interface UserService extends Remote {
    /**
     * Authenticate a user with username and password.
     * @param username The username
     * @param password The password
     * @return "SUCCESS: " + token or error message
     */
    String authenticate(String username, String password) throws RemoteException;

    /**
     * Register a new user account.
     * @param username The new username
     * @param password The new password
     * @return Success or error message
     */
    String register(String username, String password) throws RemoteException;

    /**
     * Reset password for a user.
     * @param username The username
     * @param oldPassword The old password
     * @param newPassword The new password
     * @return Success or error message
     */
    String resetPassword(String username, String oldPassword, String newPassword) throws RemoteException;

    /**
     * Admin function: change password for any user.
     * @param requesterUsername The username making the request (must be admin)
     * @param targetUsername The user whose password is being changed
     * @param newPassword The new password
     * @return Success or error message
     */
    String adminSetPassword(String sessionToken, String targetUsername, String newPassword) throws RemoteException;

    /**
     * Admin function: delete a user account.
     * @param requesterUsername The username making the request (must be admin)
     * @param targetUsername The user to delete
     * @return Success or error message
     */
    String deleteUser(String sessionToken, String targetUsername) throws RemoteException;

    /**
     * Update password for a user.
     * <p>
     * This method is required to be RMI-compliant, meaning it must declare {@link RemoteException}.
     * For non-admin callers, {@code requesterUsername} must equal {@code targetUsername} and
     * {@code oldPassword} must be correct.
     * </p>
     */
    String updatePassword(String sessionToken, String targetUsername, String oldPassword, String newPassword) throws RemoteException;

    /**
     * Get all usernames in the system (admin only).
     * @param requesterUsername The username making the request (must be admin)
     * @return List of all usernames
     */
    ArrayList<String> getAllUsernames(String sessionToken) throws RemoteException;

    /**
     * Gracefully sign out a user so the session record can be cleared.
     * @param username The username that is leaving
     * @return Success or error message
     */
    String signOut(String sessionToken) throws RemoteException;

    /**
     * Update the caller's profile information.
     * Username cannot be changed; only display name and avatar.
     */
    String updateProfile(String sessionToken, String newDisplayName, byte[] avatarBytes) throws RemoteException;

    /**
     * Get a public profile (display name + avatar) for a user.
     */
    UserProfile getProfile(String sessionToken, String targetUsername) throws RemoteException;
}
