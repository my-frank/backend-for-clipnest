package com.group10.clipnest.controller;

import com.group10.clipnest.model.User;
import com.group10.clipnest.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserRepository userRepository;

    // Get all users
    @GetMapping("")
    public ResponseEntity<?> getAllUsers(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        try {
            List<User> users = userRepository.findAll();
            List<Map<String, Object>> userList = users.stream()
                .map(this::mapUserToResponse)
                .collect(Collectors.toList());

            logger.info("✅ Fetched {} users", userList.size());
            return ResponseEntity.ok(userList);

        } catch (Exception e) {
            logger.error("❌ Failed to fetch users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to fetch users");
        }
    }

    // Search users by username or name
    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(@RequestParam String q, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        try {
            List<User> users = userRepository.findByUsernameContainingIgnoreCase(q);
            
            // Also search by full name if available
            List<User> nameResults = userRepository.findAll().stream()
                .filter(user -> user.getFullName() != null && 
                               user.getFullName().toLowerCase().contains(q.toLowerCase()))
                .collect(Collectors.toList());

            // Combine and deduplicate results
            Set<String> seenEmails = new HashSet<>();
            List<User> combinedResults = new ArrayList<>();
            
            for (User user : users) {
                if (!seenEmails.contains(user.getEmail())) {
                    combinedResults.add(user);
                    seenEmails.add(user.getEmail());
                }
            }
            
            for (User user : nameResults) {
                if (!seenEmails.contains(user.getEmail())) {
                    combinedResults.add(user);
                    seenEmails.add(user.getEmail());
                }
            }

            List<Map<String, Object>> userList = combinedResults.stream()
                .limit(20) // Limit search results
                .map(this::mapUserToResponse)
                .collect(Collectors.toList());

            logger.info("✅ Search for '{}' returned {} users", q, userList.size());
            return ResponseEntity.ok(userList);

        } catch (Exception e) {
            logger.error("❌ User search failed for '{}': {}", q, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to search users");
        }
    }

    // Get user by username
    @GetMapping("/{username}")
    public ResponseEntity<?> getUserByUsername(@PathVariable String username, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            User user = userOpt.get();
            Map<String, Object> userResponse = mapUserToResponse(user);

            logger.info("✅ Fetched user: {}", username);
            return ResponseEntity.ok(userResponse);

        } catch (Exception e) {
            logger.error("❌ Failed to fetch user {}: {}", username, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to fetch user");
        }
    }

    // Get users for mentions (with follow status)
    @GetMapping("/mentions")
    public ResponseEntity<?> getUsersForMentions(@RequestParam String q, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        User currentUser = (User) authentication.getPrincipal();

        try {
            List<User> users = userRepository.findByUsernameContainingIgnoreCase(q);
            
            // Also search by full name
            List<User> nameResults = userRepository.findAll().stream()
                .filter(user -> user.getFullName() != null && 
                               user.getFullName().toLowerCase().contains(q.toLowerCase()))
                .collect(Collectors.toList());

            // Combine and deduplicate results
            Set<String> seenEmails = new HashSet<>();
            List<User> combinedResults = new ArrayList<>();
            
            for (User user : users) {
                if (!seenEmails.contains(user.getEmail())) {
                    combinedResults.add(user);
                    seenEmails.add(user.getEmail());
                }
            }
            
            for (User user : nameResults) {
                if (!seenEmails.contains(user.getEmail())) {
                    combinedResults.add(user);
                    seenEmails.add(user.getEmail());
                }
            }

            Set<String> currentUserFollowing = currentUser.getFollowing() != null ? 
                currentUser.getFollowing() : new HashSet<>();
            Set<String> currentUserFollowers = currentUser.getFollowers() != null ? 
                currentUser.getFollowers() : new HashSet<>();

            List<Map<String, Object>> userList = combinedResults.stream()
                .filter(user -> !user.getEmail().equals(currentUser.getEmail())) // Exclude self
                .limit(10) // Limit mention results
                .map(user -> {
                    Map<String, Object> userMap = mapUserToResponse(user);
                    userMap.put("isFollowing", currentUserFollowing.contains(user.getEmail()));
                    userMap.put("isFollower", currentUserFollowers.contains(user.getEmail()));
                    return userMap;
                })
                .collect(Collectors.toList());

            logger.info("✅ Mention search for '{}' returned {} users", q, userList.size());
            return ResponseEntity.ok(userList);

        } catch (Exception e) {
            logger.error("❌ Mention search failed for '{}': {}", q, e.getMessage());
            return ResponseEntity.ok(new ArrayList<>()); // Return empty list on error
        }
    }

    // Helper method to map User entity to response format
    private Map<String, Object> mapUserToResponse(User user) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("email", user.getEmail());
        userMap.put("username", user.getUsername());
        userMap.put("fullName", user.getFullName());
        userMap.put("followersCount", user.getFollowers() != null ? user.getFollowers().size() : 0);
        userMap.put("followingCount", user.getFollowing() != null ? user.getFollowing().size() : 0);
        // Add avatar field when implemented
        userMap.put("avatar", null);
        return userMap;
    }
} 