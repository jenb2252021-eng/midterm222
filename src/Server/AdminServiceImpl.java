package Server;

import JSONUtil.ActivityLogJSONUtil;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;

/**
 * Server-side implementation of AdminService.
 * Handles admin-only operations and server control.
 */
public class AdminServiceImpl extends UnicastRemoteObject implements AdminService {
    private static final long serialVersionUID = 1L;

    public AdminServiceImpl() throws RemoteException {
        super(1102);
    }

    @Override
    public ArrayList<String> getActivityLog(String sessionToken) throws RemoteException {
        String username = SessionManager.identity(sessionToken);
        if (!"admin".equals(username)) {
            throw new RemoteException("ERROR: Access Denied. Admins only.");
        }
        ArrayList<String> logs = ActivityLogJSONUtil.getLogEntries();

        return logs;
    }

    @Override
    public String getServerStatus() throws RemoteException {
        return TreeServer.isServerEnabled() ? "STATUS_ON" : "STATUS_OFF";
    }

    @Override
    public String disableServerLogin(String sessionToken) throws RemoteException {
        String username = SessionManager.identity(sessionToken);
        if (!"admin".equals(username)) {
            return "ERROR: Access Denied. Admins only.";
        }

        TreeServer.setServerEnabled(false);
        ActivityLogJSONUtil.log("admin", "SERVER_CONTROL", "Server login disabled (rangers cannot log in)");
        return "OK";
    }

    @Override
    public String enableServerLogin(String sessionToken) throws RemoteException {
        String username = SessionManager.identity(sessionToken);
        if (!"admin".equals(username)) {
            return "ERROR: Access Denied. Admins only.";
        }

        TreeServer.setServerEnabled(true);
        ActivityLogJSONUtil.log("admin", "SERVER_CONTROL", "Server login enabled");
        return "OK";
    }
}
