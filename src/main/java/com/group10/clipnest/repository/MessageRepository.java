package com.group10.clipnest.repository;

import com.group10.clipnest.model.Message;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {
    
    // Get conversation between two users (sorted by timestamp)
    @Query("{ $or: [ " +
           "{ $and: [ { 'senderId': ?0 }, { 'receiverId': ?1 } ] }, " +
           "{ $and: [ { 'senderId': ?1 }, { 'receiverId': ?0 } ] } " +
           "], 'isGroupMessage': false }")
    List<Message> findConversationBetweenUsers(String userId1, String userId2);
    
    // Get messages sent to a user (for notifications/unread count)
    List<Message> findByReceiverIdAndIsReadFalseOrderByTimestampDesc(String receiverId);
    
    // Get all conversations for a user (latest message from each conversation)
    @Query("{ $or: [ { 'senderId': ?0 }, { 'receiverId': ?0 } ], 'isGroupMessage': false }")
    List<Message> findAllConversationsForUser(String userId);
    
    // Get group messages
    List<Message> findByGroupIdOrderByTimestampAsc(String groupId);
    
    // Get unread count for a conversation
    long countByReceiverIdAndSenderIdAndIsReadFalse(String receiverId, String senderId);
} 