package Server;

import Model.Tree;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

/**
 * Remote interface for tree-related operations.
 * Both admins and rangers can use these methods.
 * <p>RMI: every method must declare {@code throws RemoteException} (same as {@link Remote}).</p>
 */
public interface TreeService extends Remote {
    /**
     * Add a new tree. Admins add directly, rangers add to pending queue.
     * @param tree The tree to add
     * @param username The username of the requester
     * @return Success or error message
     */
    String addTree(Tree tree, String sessionToken) throws RemoteException;

    /**
     * Get all trees in the inventory.
     * @return List of all trees
     */
    ArrayList<String> getMySubmissions(String sessionToken) throws RemoteException;

    ArrayList<Tree> getAllTrees(String sessionToken) throws RemoteException;

    /**
     * Update an existing tree.
     * @param tree The tree with updated information
     * @param username The username of the requester
     * @return Success or error message
     */
    String updateTree(Tree tree, String sessionToken) throws RemoteException;

    /**
     * Delete a tree by ID.
     * @param id The tree ID
     * @param username The username of the requester
     * @return Success or error message
     */
    String deleteTree(int id, String sessionToken) throws RemoteException;

    /**
     * Search trees by species.
     * @param species The species to search for
     * @return List of matching trees
     */
    ArrayList<Tree> searchBySpecies(String species, String sessionToken) throws RemoteException;

    /**
     * Search trees by status.
     * @param status The status to search for
     * @return List of matching trees
     */
    ArrayList<Tree> searchByStatus(String status, String sessionToken) throws RemoteException;

    /**
     * Get deleted trees (admin only).
     * @param username The username of the requester
     * @return List of deleted trees
     */
    ArrayList<Tree> getDeletedTrees(String sessionToken) throws RemoteException;

    /**
     * Get all pending trees (admin only).
     * @param username The username of the requester
     * @return List of pending trees
     */
    ArrayList<Tree> getPendingTrees(String sessionToken) throws RemoteException;

    /**
     * Approve a pending tree (admin only).
     * @param pendingId The pending tree ID
     * @param username The username of the requester
     * @return Success or error message
     */
    String approvePendingTree(int pendingId, String sessionToken) throws RemoteException;

    /**
     * Reject a pending tree (admin only).
     * @param pendingId The pending tree ID
     * @param username The username of the requester
     * @return Success or error message
     */
    String rejectPendingTree(int pendingId, String sessionToken) throws RemoteException;

    /**
     * Monitoring history of one registered tree, built from the server activity log
     * (registration, review, measurement and status updates). Available to any signed-in user.
     * @param treeId The tree ID
     * @return Lines formatted as {@code "yyyy-MM-dd HH:mm | user | event"}, newest first
     */
    ArrayList<String> getTreeHistory(int treeId, String sessionToken) throws RemoteException;

    /**
     * Restore an archived (deleted) tree to the active inventory with its original ID and photo (admin only).
     * @return Success or error message
     */
    String restoreDeletedTree(int treeId, String sessionToken) throws RemoteException;

    /**
     * Permanently remove a record from the archive, including its archived photo (admin only). Cannot be undone.
     * @return Success or error message
     */
    String purgeDeletedTree(int treeId, String sessionToken) throws RemoteException;
}
