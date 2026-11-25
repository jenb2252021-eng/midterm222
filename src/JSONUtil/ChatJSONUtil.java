package JSONUtil;

import Model.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import storage.AppDataPaths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe JSON persistence layer for chat messages.
 * All messages are stored in a main file, while deleted messages
 * are copied into a separate audit file for admin review.
 */
public class ChatJSONUtil {
    private static final File CHAT_FILE = AppDataPaths.dataFile("chat_messages.json");
    private static final File DELETED_FILE = AppDataPaths.dataFile("deleted_chat_messages.json");

    private static final ArrayList<ChatMessage> messages = new ArrayList<>();
    private static final ArrayList<ChatMessage> deletedMessages = new ArrayList<>();

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper MAPPER = JacksonTreeIO.mapper();

    static {
        loadFromJson();
        loadDeletedFromJson();
    }

    public static ArrayList<ChatMessage> getConversation(String userA, String userB) {
        String convId = conversationId(userA, userB);
        lock.readLock().lock();
        try {
            ArrayList<ChatMessage> conv = new ArrayList<>();
            for (ChatMessage m : messages) {
                if (convId.equals(m.conversationId)) {
                    conv.add(copyOf(m));
                }
            }
            conv.sort(Comparator.comparing(m -> m.timestamp));
            return conv;
        } finally {
            lock.readLock().unlock();
        }
    }

    public static ArrayList<ChatMessage> getDeletedMessages() {
        lock.readLock().lock();
        try {
            ArrayList<ChatMessage> list = new ArrayList<>();
            for (ChatMessage m : deletedMessages) {
                list.add(copyOf(m));
            }
            list.sort(Comparator.comparing(m -> m.timestamp));
            return list;
        } finally {
            lock.readLock().unlock();
        }
    }

    public static ChatMessage addMessage(String sender, String recipient, String content) {
        String trimmedContent = content == null ? "" : content.trim();
        if (trimmedContent.isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }
        String id = UUID.randomUUID().toString();
        String convId = conversationId(sender, recipient);
        String now = dtf.format(LocalDateTime.now());

        ChatMessage msg = new ChatMessage(
                id,
                convId,
                sender,
                recipient,
                "TEXT",
                trimmedContent,
                null,
                null,
                now,
                false,
                null,
                false,
                null,
                null,
                false,
                null);

        lock.writeLock().lock();
        try {
            messages.add(msg);
            persist();
            return copyOf(msg);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static ChatMessage addAttachmentMessage(String sender,
                                                   String recipient,
                                                   String messageType,
                                                   String content,
                                                   String fileName,
                                                   String fileBase64) {
        String safeType = (messageType == null || messageType.trim().isEmpty()) ? "FILE" : messageType.trim().toUpperCase();
        String safeContent = content == null ? "" : content.trim();
        String safeName = fileName == null ? "" : fileName.trim();
        if (safeName.isEmpty()) {
            throw new IllegalArgumentException("File name is required for attachments");
        }
        if (fileBase64 == null || fileBase64.trim().isEmpty()) {
            throw new IllegalArgumentException("File data is required for attachments");
        }

        String id = UUID.randomUUID().toString();
        String convId = conversationId(sender, recipient);
        String now = dtf.format(LocalDateTime.now());

        ChatMessage msg = new ChatMessage(
                id,
                convId,
                sender,
                recipient,
                safeType,
                safeContent,
                safeName,
                fileBase64,
                now,
                false,
                null,
                false,
                null,
                null,
                false,
                null);

        lock.writeLock().lock();
        try {
            messages.add(msg);
            persist();
            return copyOf(msg);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static ChatMessage editMessage(String requester, String messageId, String newContent) {
        String trimmedContent = newContent == null ? "" : newContent.trim();
        if (trimmedContent.isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }
        lock.writeLock().lock();
        try {
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage existing = messages.get(i);
                if (existing.id.equals(messageId)) {
                    if (!requester.equals(existing.sender)) {
                        throw new SecurityException("Only the sender can edit this message");
                    }
                    if (existing.deleted) {
                        throw new IllegalStateException("Cannot edit a deleted message");
                    }
                    existing.content = trimmedContent;
                    existing.edited = true;
                    existing.editedAt = dtf.format(LocalDateTime.now());
                    persist();
                    return copyOf(existing);
                }
            }
            throw new IllegalArgumentException("Message not found");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Marks a message as deleted and records a copy in the deleted audit list.
     * If unsend is true, the message will be treated as removed for both users
     * (but still retained in the deleted audit file).
     */
    public static String deleteMessage(String requester, String messageId, boolean unsend) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage existing = messages.get(i);
                if (existing.id.equals(messageId)) {
                    if (!requester.equals(existing.sender) && !"admin".equals(requester)) {
                        throw new SecurityException("Only the sender or an admin can delete this message");
                    }
                    if (existing.deleted) {
                        return "ERROR: Message already deleted.";
                    }
                    existing.deleted = true;
                    existing.deletedAt = dtf.format(LocalDateTime.now());
                    existing.deletedBy = requester;

                    // Copy to deleted list for admin audit
                    ChatMessage auditCopy = copyOf(existing);
                    deletedMessages.add(auditCopy);

                    if (unsend) {
                        existing.content = "[message unsent]";
                    }

                    persist();
                    persistDeleted();
                    return "SUCCESS: Message deleted.";
                }
            }
            return "ERROR: Message not found.";
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Marks all messages in the conversation from fromUser -> recipient as read
     * the first time the recipient opens the conversation.
     */
    public static void markConversationRead(String recipient, String fromUser) {
        String convId = conversationId(recipient, fromUser);
        String now = dtf.format(LocalDateTime.now());
        boolean changed = false;

        lock.writeLock().lock();
        try {
            for (ChatMessage m : messages) {
                if (!convId.equals(m.conversationId)) continue;
                if (m.deleted) continue;
                if (!recipient.equals(m.recipient)) continue;
                if (m.readByRecipient) continue;
                m.readByRecipient = true;
                m.readAt = now;
                changed = true;
            }
            if (changed) {
                persist();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static int getUnreadCount(String username) {
        String user = (username == null) ? "" : username.trim();
        if (user.isEmpty()) return 0;
        int count = 0;
        lock.readLock().lock();
        try {
            for (ChatMessage m : messages) {
                if (m.deleted) continue;
                if (!user.equals(m.recipient)) continue;
                if (!m.readByRecipient) {
                    count++;
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        return count;
    }

    private static String conversationId(String a, String b) {
        String u1 = a == null ? "" : a.trim().toLowerCase();
        String u2 = b == null ? "" : b.trim().toLowerCase();
        if (u1.compareTo(u2) <= 0) {
            return u1 + "|" + u2;
        }
        return u2 + "|" + u1;
    }

    private static void loadFromJson() {
        lock.writeLock().lock();
        try {
            messages.clear();
            if (!CHAT_FILE.exists()) {
                return;
            }
            String raw = new String(Files.readAllBytes(CHAT_FILE.toPath()), StandardCharsets.UTF_8);
            if (raw.trim().isEmpty()) {
                return;
            }
            JsonNode root = MAPPER.readTree(raw);
            ArrayNode arr = JacksonTreeIO.asArray(root.get("messages"), MAPPER);
            for (JsonNode n : arr) {
                messages.add(MAPPER.treeToValue(n, ChatMessage.class));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static void loadDeletedFromJson() {
        lock.writeLock().lock();
        try {
            deletedMessages.clear();
            if (!DELETED_FILE.exists()) {
                return;
            }
            String raw = new String(Files.readAllBytes(DELETED_FILE.toPath()), StandardCharsets.UTF_8);
            if (raw.trim().isEmpty()) {
                return;
            }
            JsonNode root = MAPPER.readTree(raw);
            ArrayNode arr = JacksonTreeIO.asArray(root.get("deletedMessages"), MAPPER);
            for (JsonNode n : arr) {
                deletedMessages.add(MAPPER.treeToValue(n, ChatMessage.class));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static void persist() {
        ChatFile doc = new ChatFile();
        doc.messages = new ArrayList<>(messages);
        try {
            Files.createDirectories(CHAT_FILE.toPath().getParent());
            MAPPER.writeValue(CHAT_FILE, doc);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist chat messages", e);
        }
    }

    private static void persistDeleted() {
        DeletedChatFile doc = new DeletedChatFile();
        doc.deletedMessages = new ArrayList<>(deletedMessages);
        try {
            Files.createDirectories(DELETED_FILE.toPath().getParent());
            MAPPER.writeValue(DELETED_FILE, doc);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist deleted chat messages", e);
        }
    }

    private static class ChatFile {
        public List<ChatMessage> messages;
    }

    private static class DeletedChatFile {
        public List<ChatMessage> deletedMessages;
    }

    private static ChatMessage copyOf(ChatMessage m) {
        return new ChatMessage(
                m.id,
                m.conversationId,
                m.sender,
                m.recipient,
                m.messageType,
                m.content,
                m.fileName,
                m.fileBase64,
                m.timestamp,
                m.edited,
                m.editedAt,
                m.deleted,
                m.deletedAt,
                m.deletedBy,
                m.readByRecipient,
                m.readAt
        );
    }
}
