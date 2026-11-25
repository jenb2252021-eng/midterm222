package Server;

import JSONUtil.ActivityLogJSONUtil;
import JSONUtil.ChatJSONUtil;
import JSONUtil.UserJSONUtil;
import Model.ChatMessage;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Set;

/**
 * RMI implementation of the ChatService.
 * Delegates persistence to ChatJSONUtil and uses SessionManager
 * to determine which users are currently online.
 */
public class ChatServiceImpl extends UnicastRemoteObject implements ChatService {

    private static final long serialVersionUID = 1L;

    public ChatServiceImpl() throws RemoteException {
        super(1103);
    }

    @Override
    public ArrayList<String> getOnlineUsers(String sessionToken) throws RemoteException {
        String requesterUsername = SessionManager.identity(sessionToken);
        Set<String> active = SessionManager.getActiveUsers();
        ArrayList<String> result = new ArrayList<>(active);
        result.sort(String::compareToIgnoreCase);
        return result;
    }

    @Override
    public ArrayList<String> getAllUsers(String sessionToken) throws RemoteException {
        String requesterUsername = SessionManager.identity(sessionToken);
        ArrayList<String> users = UserJSONUtil.getAllUsernames();
        users.sort(String::compareToIgnoreCase);
        return users;
    }

    @Override
    public ArrayList<ChatMessage> getConversation(String sessionToken, String otherUsername) throws RemoteException {
        String requesterUsername = SessionManager.identity(sessionToken);
        if (requesterUsername == null || requesterUsername.trim().isEmpty()) {
            throw new RemoteException("ERROR: requester username is required.");
        }
        if (otherUsername == null || otherUsername.trim().isEmpty()) {
            throw new RemoteException("ERROR: other username is required.");
        }

        // Mark messages from otherUsername -> requesterUsername as read
        ChatJSONUtil.markConversationRead(requesterUsername, otherUsername);
        return ChatJSONUtil.getConversation(requesterUsername, otherUsername);
    }

    @Override
    public ChatMessage sendMessage(String sessionToken, String recipientUsername, String content) throws RemoteException {
        String senderUsername = SessionManager.identity(sessionToken);
        if (senderUsername == null || senderUsername.trim().isEmpty()) {
            throw new RemoteException("ERROR: sender username is required.");
        }
        if (recipientUsername == null || recipientUsername.trim().isEmpty()) {
            throw new RemoteException("ERROR: recipient username is required.");
        }
        try {
            ChatMessage msg = ChatJSONUtil.addMessage(senderUsername, recipientUsername, content);
            ActivityLogJSONUtil.log(senderUsername, "CHAT_SEND", "To " + recipientUsername + " | Message: " + msg.id);
            return msg;
        } catch (IllegalArgumentException e) {
            throw new RemoteException("ERROR: " + e.getMessage(), e);
        }
    }

    @Override
    public ChatMessage sendAttachment(String sessionToken,
                                      String recipientUsername,
                                      String fileName,
                                      String messageType,
                                      byte[] data,
                                      String caption) throws RemoteException {
        String senderUsername = SessionManager.identity(sessionToken);
        if (senderUsername == null || senderUsername.trim().isEmpty()) {
            throw new RemoteException("ERROR: sender username is required.");
        }
        if (recipientUsername == null || recipientUsername.trim().isEmpty()) {
            throw new RemoteException("ERROR: recipient username is required.");
        }
        if (data == null || data.length == 0) {
            throw new RemoteException("ERROR: attachment data is empty.");
        }
        if (data.length > 20L * 1024L * 1024L) {
            throw new RemoteException("ERROR: attachment exceeds 20 MB limit.");
        }
        String type = (messageType == null ? "IMAGE" : messageType.trim().toUpperCase());
        if (!"IMAGE".equals(type)) {
            throw new RemoteException("ERROR: only image attachments are supported.");
        }
        try {
            String base64 = Base64.getEncoder().encodeToString(data);
            ChatMessage msg = ChatJSONUtil.addAttachmentMessage(
                    senderUsername,
                    recipientUsername,
                    "IMAGE",
                    caption,
                    fileName,
                    base64
            );
            ActivityLogJSONUtil.log(senderUsername, "CHAT_SEND_ATTACHMENT",
                    "To " + recipientUsername + " file=" + fileName + " type=" + messageType);
            return msg;
        } catch (IllegalArgumentException e) {
            throw new RemoteException("ERROR: " + e.getMessage(), e);
        }
    }

    @Override
    public ChatMessage editMessage(String sessionToken, String messageId, String newContent) throws RemoteException {
        String requesterUsername = SessionManager.identity(sessionToken);
        if (requesterUsername == null || requesterUsername.trim().isEmpty()) {
            throw new RemoteException("ERROR: requester username is required.");
        }
        if (messageId == null || messageId.trim().isEmpty()) {
            throw new RemoteException("ERROR: messageId is required.");
        }
        try {
            ChatMessage msg = ChatJSONUtil.editMessage(requesterUsername, messageId, newContent);
            ActivityLogJSONUtil.log(requesterUsername, "CHAT_EDIT", "Edited message " + messageId);
            return msg;
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new RemoteException("ERROR: " + e.getMessage(), e);
        } catch (SecurityException se) {
            throw new RemoteException("ERROR: " + se.getMessage(), se);
        }
    }

    @Override
    public String deleteMessage(String sessionToken, String messageId, boolean unsend) throws RemoteException {
        String requesterUsername = SessionManager.identity(sessionToken);
        if (requesterUsername == null || requesterUsername.trim().isEmpty()) {
            throw new RemoteException("ERROR: requester username is required.");
        }
        if (messageId == null || messageId.trim().isEmpty()) {
            throw new RemoteException("ERROR: messageId is required.");
        }
        try {
            String result = ChatJSONUtil.deleteMessage(requesterUsername, messageId, unsend);
            ActivityLogJSONUtil.log(requesterUsername, unsend ? "CHAT_UNSEND" : "CHAT_DELETE", "Message " + messageId);
            return result;
        } catch (SecurityException se) {
            throw new RemoteException("ERROR: " + se.getMessage(), se);
        }
    }

    @Override
    public ArrayList<ChatMessage> getDeletedMessages(String sessionToken) throws RemoteException {
        String requesterUsername = SessionManager.identity(sessionToken);
        if (!"admin".equals(requesterUsername)) {
            throw new RemoteException("ERROR: Access denied. Admin only.");
        }
        return ChatJSONUtil.getDeletedMessages();
    }

    @Override
    public int getUnreadCount(String sessionToken) throws RemoteException {
        String username = SessionManager.identity(sessionToken);
        return ChatJSONUtil.getUnreadCount(username);
    }
}
