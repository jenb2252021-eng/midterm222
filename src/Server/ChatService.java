package Server;

import Model.ChatMessage;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

/**
 * Remote chat service for admin and ranger communication.
 * Supports direct messaging, editing, deletion/unsend, and
 * online member listing.
 */
public interface ChatService extends Remote {

    /**
     * Returns the list of currently online users based on active sessions.
     */
    ArrayList<String> getOnlineUsers(String sessionToken) throws RemoteException;

    /**
     * Returns all registered users in the system so the
     * UI can show both online and offline members.
     */
    ArrayList<String> getAllUsers(String sessionToken) throws RemoteException;

    /**
     * Returns the full conversation history between two users,
     * ordered by timestamp ascending.
     */
    ArrayList<ChatMessage> getConversation(String sessionToken, String otherUsername) throws RemoteException;

    /**
     * Sends a new message from sender to recipient.
     */
    ChatMessage sendMessage(String sessionToken, String recipientUsername, String content) throws RemoteException;

    /**
     * Sends an attachment (image or generic file) from sender to recipient.
     * messageType should be "IMAGE" or "FILE".
     */
    ChatMessage sendAttachment(String sessionToken,
                               String recipientUsername,
                               String fileName,
                               String messageType,
                               byte[] data,
                               String caption) throws RemoteException;

    /**
     * Edit the content of a previously sent message.
     * Only the original sender may edit the message.
     */
    ChatMessage editMessage(String sessionToken, String messageId, String newContent) throws RemoteException;

    /**
     * Delete or unsend a message.
     * Only the original sender or admin may perform this action.
     */
    String deleteMessage(String sessionToken, String messageId, boolean unsend) throws RemoteException;

    /**
     * Admin-only view of all deleted/unsent messages.
     */
    ArrayList<ChatMessage> getDeletedMessages(String sessionToken) throws RemoteException;

    /**
     * Returns the total number of unread messages for this user.
     */
    int getUnreadCount(String sessionToken) throws RemoteException;
}
