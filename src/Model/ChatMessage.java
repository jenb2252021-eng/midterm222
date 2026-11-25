package Model;

import java.io.Serializable;

/**
 * Represents a single chat message between two users.
 * Stored on disk via JSON and transported over RMI.
 */
public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public String id;
    public String conversationId;
    public String sender;
    public String recipient;

    /**
     * Message type: "TEXT", "IMAGE", or "FILE".
     */
    public String messageType;

    /**
     * For text messages, this is the text body.
     * For file/image messages, this is an optional caption.
     */
    public String content;

    /**
     * Original file name for attachments (IMAGE/FILE).
     */
    public String fileName;

    /**
     * Base64 encoded file contents for attachments.
     * Null for plain text messages.
     */
    public String fileBase64;

    public String timestamp;
    public boolean edited;
    public String editedAt;
    public boolean deleted;
    public String deletedAt;
    public String deletedBy;

    /**
     * True when the message has been read by its recipient.
     */
    public boolean readByRecipient;

    /**
     * Timestamp when the recipient first read this message.
     */
    public String readAt;

    public ChatMessage() {
    }

    public ChatMessage(String id,
                       String conversationId,
                       String sender,
                       String recipient,
                       String messageType,
                       String content,
                       String fileName,
                       String fileBase64,
                       String timestamp,
                       boolean edited,
                       String editedAt,
                       boolean deleted,
                       String deletedAt,
                       String deletedBy,
                       boolean readByRecipient,
                       String readAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.sender = sender;
        this.recipient = recipient;
        this.messageType = messageType;
        this.content = content;
        this.fileName = fileName;
        this.fileBase64 = fileBase64;
        this.timestamp = timestamp;
        this.edited = edited;
        this.editedAt = editedAt;
        this.deleted = deleted;
        this.deletedAt = deletedAt;
        this.deletedBy = deletedBy;
        this.readByRecipient = readByRecipient;
        this.readAt = readAt;
    }
}

