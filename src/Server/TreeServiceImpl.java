package Server;

import Model.Tree;
import JSONUtil.ActivityLogJSONUtil;
import JSONUtil.DeletedTreeJSONUtil;
import JSONUtil.PendingTreeJSONUtil;
import JSONUtil.TreeJSONUtil;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Server-side implementation of TreeService.
 * Handles all tree-related operations.
 */
public class TreeServiceImpl extends UnicastRemoteObject implements TreeService {
    private static final long serialVersionUID = 1L;
    private static final ReentrantReadWriteLock treeLock = new ReentrantReadWriteLock();

    public TreeServiceImpl() throws RemoteException {
        super(1100);
    }

    @Override
    public String addTree(Tree tree, String sessionToken) throws RemoteException {
        String username = SessionManager.identity(sessionToken);
        // Set the owner as the requester
        if (tree == null) return "ERROR: Tree record required";
        tree.id = 0;
        tree.imagePath = null;
        tree.ownerUsername = username;

        if (!TreeJSONUtil.isValidStatus(tree.status)) {
            return "ERROR: Invalid tree status";
        }

        treeLock.writeLock().lock();
        try {
            if ("admin".equals(username)) {
                String result = TreeJSONUtil.addTree(tree);
                if (result.startsWith("SUCCESS")) {
                    ActivityLogJSONUtil.log(username, "ADD_TREE", "Species: " + tree.species + ", Location: " + tree.location);
                }
                return result;
            } else {
                String result = PendingTreeJSONUtil.addPendingTree(tree);
                if (result.startsWith("SUCCESS")) {
                    ActivityLogJSONUtil.log(username, "SUBMIT_PENDING_TREE", "Species: " + tree.species + ", Location: " + tree.location);
                }
                return result;
            }
        } finally {
            treeLock.writeLock().unlock();
        }
    }

    @Override
    public ArrayList<String> getMySubmissions(String sessionToken) throws RemoteException {
        String ranger = SessionManager.identity(sessionToken);
        treeLock.readLock().lock();
        try {
            ArrayList<String> items = new ArrayList<>();
            for (Tree t : PendingTreeJSONUtil.getAllPending()) {
                if (ranger.equals(t.ownerUsername)) items.add("Pending review  ·  " + t.species + "  #" + t.id);
            }
            for (Tree t : TreeJSONUtil.getAllTrees()) {
                if (ranger.equals(t.ownerUsername)) items.add("Approved  ·  " + t.species + "  #" + t.id);
            }
            items.addAll(ActivityLogJSONUtil.rejectedSubmissions(ranger));
            return items;
        } finally { treeLock.readLock().unlock(); }
    }

    @Override
    public ArrayList<Tree> getAllTrees(String sessionToken) throws RemoteException {
        SessionManager.identity(sessionToken);
        treeLock.readLock().lock();
        try {
            ArrayList<Tree> allTrees = TreeJSONUtil.getAllTrees();
            for (Tree t : allTrees) {
                TreeJSONUtil.hydrateTreeImageForClient(t);
            }
            java.util.Collections.reverse(allTrees);
            return allTrees;
        } finally {
            treeLock.readLock().unlock();
        }
    }

    @Override
    public String updateTree(Tree tree, String sessionToken) throws RemoteException {
        String username = SessionManager.identity(sessionToken);
        treeLock.writeLock().lock();
        try {
            if (tree == null) return "ERROR: Tree record required";
            Tree existing = TreeJSONUtil.getTreeById(tree.id);
            if (existing == null) {
                return "ERROR: Tree ID not found";
            }

            // Enforce ownership
            if (!"admin".equals(username)) {
                String owner = existing.ownerUsername;
                if (owner == null || !owner.equals(username)) {
                    return "ERROR: You are not allowed to edit this tree.";
                }
            }

            // Preserve original owner
            tree.ownerUsername = existing.ownerUsername;
            tree.imagePath = existing.imagePath;

            String result = TreeJSONUtil.updateTree(tree);
            if (result.startsWith("SUCCESS")) {
                ActivityLogJSONUtil.log(username, "UPDATE_TREE", "Updated Tree ID: " + tree.id);
            }
            return result;
        } finally {
            treeLock.writeLock().unlock();
        }
    }

    @Override
    public String deleteTree(int id, String sessionToken) throws RemoteException {
        String username = SessionManager.identity(sessionToken);
        treeLock.writeLock().lock();
        try {
            Tree t = TreeJSONUtil.getTreeById(id);
            if (t != null) {
                // Enforce ownership
                if (!"admin".equals(username)) {
                    String owner = t.ownerUsername;
                    if (owner == null || !owner.equals(username)) {
                        return "ERROR: You are not allowed to delete this tree.";
                    }
                }

                DeletedTreeJSONUtil.addDeletedTree(t);
                String result = TreeJSONUtil.deleteTree(id);
                ActivityLogJSONUtil.log(username, "DELETE_TREE", "Deleted Tree ID: " + id + " (" + t.species + ")");
                return result;
            } else {
                return "ERROR: Tree not found";
            }
        } finally {
            treeLock.writeLock().unlock();
        }
    }

    @Override
    public ArrayList<Tree> searchBySpecies(String species, String sessionToken) throws RemoteException {
        SessionManager.identity(sessionToken);
        treeLock.readLock().lock();
        try {
            return TreeJSONUtil.searchBySpecies(species);
        } finally {
            treeLock.readLock().unlock();
        }
    }

    @Override
    public ArrayList<Tree> searchByStatus(String status, String sessionToken) throws RemoteException {
        SessionManager.identity(sessionToken);
        treeLock.readLock().lock();
        try {
            if (!TreeJSONUtil.isValidStatus(status)) {
                return new ArrayList<>();
            }
            return TreeJSONUtil.searchByStatus(status);
        } finally {
            treeLock.readLock().unlock();
        }
    }

    @Override
    public ArrayList<Tree> getDeletedTrees(String sessionToken) throws RemoteException {
        String username = SessionManager.identity(sessionToken);
        if (!"admin".equals(username)) {
            throw new RemoteException("ERROR: Access Denied. Admins only.");
        }
        treeLock.readLock().lock();
        try {
            return DeletedTreeJSONUtil.getDeletedTrees();
        } finally {
            treeLock.readLock().unlock();
        }
    }

    @Override
    public ArrayList<Tree> getPendingTrees(String sessionToken) throws RemoteException {
        String username = SessionManager.identity(sessionToken);
        if (!"admin".equals(username)) {
            throw new RemoteException("ERROR: Access Denied. Admins only.");
        }
        treeLock.readLock().lock();
        try {
            ArrayList<Tree> pending = PendingTreeJSONUtil.getAllPending();
            for (Tree t : pending) {
                PendingTreeJSONUtil.hydratePendingImageForClient(t);
            }
            return pending;
        } finally {
            treeLock.readLock().unlock();
        }
    }

    @Override
    public String approvePendingTree(int pendingId, String sessionToken) throws RemoteException {
        String username = SessionManager.identity(sessionToken);
        if (!"admin".equals(username)) {
            return "ERROR: Access Denied. Admins only.";
        }

        treeLock.writeLock().lock();
        try {
            Tree pending = PendingTreeJSONUtil.getPendingById(pendingId);
            if (pending == null) {
                return "ERROR: Pending tree not found";
            }

            String result = TreeJSONUtil.addTreeWithId(pending);
            if (result.startsWith("SUCCESS")) {
                PendingTreeJSONUtil.removePending(pendingId);
                ActivityLogJSONUtil.log("admin", "APPROVE_PENDING_TREE",
                        "Approved pending ID " + pendingId + " (" + pending.species + ")");
            }
            return result;
        } finally {
            treeLock.writeLock().unlock();
        }
    }

    @Override
    public String rejectPendingTree(int pendingId, String sessionToken) throws RemoteException {
        String username = SessionManager.identity(sessionToken);
        if (!"admin".equals(username)) {
            return "ERROR: Access Denied. Admins only.";
        }

        treeLock.writeLock().lock();
        try {
            Tree pending = PendingTreeJSONUtil.getPendingById(pendingId);
            String result = PendingTreeJSONUtil.removePending(pendingId);
            if (pending != null) {
                ActivityLogJSONUtil.log(pending.ownerUsername, "SUBMISSION_REJECTED", pending.species + "  #" + pending.id);
                ActivityLogJSONUtil.log("admin", "REJECT_PENDING_TREE",
                        "Rejected pending ID " + pendingId + " (" + pending.species + ")");
            }
            return result;
        } finally {
            treeLock.writeLock().unlock();
        }
    }
}
