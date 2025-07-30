package com.group10.clipnest.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "messages")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Message {
    @Id
    private String id;
    
    private String senderId;        // Email of sender
    private String senderUsername;  // Username of sender
    private String receiverId;      // Email of receiver
    private String receiverUsername; // Username of receiver
    
    private String content;         // Message text
    private String type;           // "text", "image", "audio", etc.
    private String imageUri;       // For image messages
    private String audioUri;       // For audio messages
    
    private LocalDateTime timestamp;
    private boolean isRead;
    private boolean isDelivered;
    
    // For replies
    private String replyToMessageId;
    
    // For editing/deleting
    private boolean isEdited;
    private boolean isDeleted;
    private LocalDateTime editedAt;
    private LocalDateTime deletedAt;
    
    // For group messages (optional)
    private String groupId;
    private boolean isGroupMessage;
} 