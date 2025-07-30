package com.group10.clipnest.controller;

import com.group10.clipnest.model.Message;
import com.group10.clipnest.model.User;
import com.group10.clipnest.repository.MessageRepository;
import com.group10.clipnest.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/messages")
public class MessageController {

    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    // Send a message
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, String> request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        User sender = (User) authentication.getPrincipal();
        String receiverUsername = request.get("receiverUsername");
        String content = request.get("content");
        String type = request.getOrDefault("type", "text");

        if (receiverUsername == null || content == null) {
            return ResponseEntity.badRequest().body("Receiver username and content are required");
        }

        try {
            // Find receiver
            Optional<User> receiverOpt = userRepository.findByUsername(receiverUsername);
            if (receiverOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Receiver not found");
            }

            User receiver = receiverOpt.get();

            // Create message
            Message message = new Message();
            message.setSenderId(sender.getEmail());
            message.setSenderUsername(sender.getUsername());
            message.setReceiverId(receiver.getEmail());
            message.setReceiverUsername(receiver.getUsername());
            message.setContent(content);
            message.setType(type);
            message.setTimestamp(LocalDateTime.now());
            message.setRead(false);
            message.setDelivered(true);
            message.setGroupMessage(false);

            // Handle optional fields
            if (request.containsKey("imageUri")) {
                message.setImageUri(request.get("imageUri"));
            }
            if (request.containsKey("audioUri")) {
                message.setAudioUri(request.get("audioUri"));
            }
            if (request.containsKey("replyToMessageId")) {
                message.setReplyToMessageId(request.get("replyToMessageId"));
            }

            // Save message
            Message savedMessage = messageRepository.save(message);

            logger.info("✅ Message sent from {} to {}", sender.getUsername(), receiver.getUsername());

            // Return message data
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", mapMessageToResponse(savedMessage));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("❌ Failed to send message: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send message");
        }
    }

    // Get conversation with another user
    @GetMapping("/conversation/{username}")
    public ResponseEntity<?> getConversation(@PathVariable String username, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        User currentUser = (User) authentication.getPrincipal();

        try {
            // Find the other user
            Optional<User> otherUserOpt = userRepository.findByUsername(username);
            if (otherUserOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            User otherUser = otherUserOpt.get();

            // Get conversation messages
            List<Message> messages = messageRepository.findConversationBetweenUsers(
                currentUser.getEmail(), otherUser.getEmail());

            // Sort by timestamp
            messages.sort(Comparator.comparing(Message::getTimestamp));

            // Convert to response format
            List<Map<String, Object>> messageList = messages.stream()
                .map(this::mapMessageToResponse)
                .collect(Collectors.toList());

            logger.info("✅ Retrieved {} messages for conversation between {} and {}", 
                       messages.size(), currentUser.getUsername(), username);

            return ResponseEntity.ok(messageList);

        } catch (Exception e) {
            logger.error("❌ Failed to get conversation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to get conversation");
        }
    }

    // Get all conversations for current user
    @GetMapping("/conversations")
    public ResponseEntity<?> getAllConversations(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        User currentUser = (User) authentication.getPrincipal();

        try {
            List<Message> allMessages = messageRepository.findAllConversationsForUser(currentUser.getEmail());

            // Group messages by conversation partner
            Map<String, Message> latestMessages = new HashMap<>();
            
            for (Message msg : allMessages) {
                String partnerId = msg.getSenderId().equals(currentUser.getEmail()) 
                    ? msg.getReceiverId() : msg.getSenderId();
                
                Message existing = latestMessages.get(partnerId);
                if (existing == null || msg.getTimestamp().isAfter(existing.getTimestamp())) {
                    latestMessages.put(partnerId, msg);
                }
            }

            // Convert to conversation list
            List<Map<String, Object>> conversations = new ArrayList<>();
            for (Message msg : latestMessages.values()) {
                boolean isCurrentUserSender = msg.getSenderId().equals(currentUser.getEmail());
                String partnerEmail = isCurrentUserSender ? msg.getReceiverId() : msg.getSenderId();
                String partnerUsername = isCurrentUserSender ? msg.getReceiverUsername() : msg.getSenderUsername();

                // Get unread count
                long unreadCount = messageRepository.countByReceiverIdAndSenderIdAndIsReadFalse(
                    currentUser.getEmail(), partnerEmail);

                Map<String, Object> conversation = new HashMap<>();
                conversation.put("id", partnerUsername);
                conversation.put("username", partnerUsername);
                conversation.put("name", partnerUsername); // Could be enhanced with full names
                conversation.put("lastMessage", msg.getContent());
                conversation.put("lastTimestamp", msg.getTimestamp().toString());
                conversation.put("unreadCount", unreadCount);
                conversation.put("isGroup", false);

                conversations.add(conversation);
            }

            // Sort by last message timestamp (newest first)
            conversations.sort((a, b) -> {
                String timeA = (String) a.get("lastTimestamp");
                String timeB = (String) b.get("lastTimestamp");
                return timeB.compareTo(timeA);
            });

            logger.info("✅ Retrieved {} conversations for {}", conversations.size(), currentUser.getUsername());

            return ResponseEntity.ok(conversations);

        } catch (Exception e) {
            logger.error("❌ Failed to get conversations: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to get conversations");
        }
    }

    // Mark messages as read
    @PostMapping("/mark-read/{username}")
    public ResponseEntity<?> markMessagesAsRead(@PathVariable String username, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        User currentUser = (User) authentication.getPrincipal();

        try {
            Optional<User> senderOpt = userRepository.findByUsername(username);
            if (senderOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            User sender = senderOpt.get();

            // Find unread messages from this sender to current user
            List<Message> unreadMessages = messageRepository.findByReceiverIdAndIsReadFalseOrderByTimestampDesc(
                currentUser.getEmail());

            // Filter messages from specific sender and mark as read
            int markedCount = 0;
            for (Message msg : unreadMessages) {
                if (msg.getSenderId().equals(sender.getEmail())) {
                    msg.setRead(true);
                    messageRepository.save(msg);
                    markedCount++;
                }
            }

            logger.info("✅ Marked {} messages as read for {} from {}", 
                       markedCount, currentUser.getUsername(), username);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("markedCount", markedCount);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("❌ Failed to mark messages as read: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to mark messages as read");
        }
    }

    // Helper method to convert Message to response format
    private Map<String, Object> mapMessageToResponse(Message message) {
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("id", message.getId());
        messageMap.put("senderId", message.getSenderId());
        messageMap.put("senderUsername", message.getSenderUsername());
        messageMap.put("receiverId", message.getReceiverId());
        messageMap.put("receiverUsername", message.getReceiverUsername());
        messageMap.put("content", message.getContent());
        messageMap.put("type", message.getType());
        messageMap.put("timestamp", message.getTimestamp().toString());
        messageMap.put("isRead", message.isRead());
        messageMap.put("isDelivered", message.isDelivered());
        messageMap.put("isEdited", message.isEdited());
        messageMap.put("isDeleted", message.isDeleted());
        
        if (message.getImageUri() != null) {
            messageMap.put("imageUri", message.getImageUri());
        }
        if (message.getAudioUri() != null) {
            messageMap.put("audioUri", message.getAudioUri());
        }
        if (message.getReplyToMessageId() != null) {
            messageMap.put("replyToMessageId", message.getReplyToMessageId());
        }

        return messageMap;
    }
} 