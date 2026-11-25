package Server;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

/**
 * Remote interface for admin-only operations.
 */
public interface AdminService extends Remote {
    /**
     * Get the activity log (admin only).
     * @param username The username of the requester (must be admin)
     * @return List of log entries
     */
    ArrayList<String> getActivityLog(String sessionToken) throws RemoteException;

    /**
     * Get server enabled status.
     * @return "STATUS_ON" or "STATUS_OFF"
     */
    String getServerStatus() throws RemoteException;

    /**
     * Disable server login for non-admin users (admin only).
     * @param username The username of the requester (must be admin)
     * @return "OK" or error message
     */
    String disableServerLogin(String sessionToken) throws RemoteException;

    /**
     * Enable server login for all users (admin only).
     * @param username The username of the requester (must be admin)
     * @return "OK" or error message
     */
    String enableServerLogin(String sessionToken) throws RemoteException;
}
