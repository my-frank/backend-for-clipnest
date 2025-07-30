package com.group10.clipnest.controller;

import com.group10.clipnest.model.User;
import com.group10.clipnest.repository.UserRepository;
import com.group10.clipnest.security.JwtUtil;
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
@RequestMapping("/api/follow")
public class FollowController {

    private static final Logger logger = LoggerFactory.getLogger(FollowController.class);

    @Autowired
    private UserRepository userRepository;

    // Follow a user
    @PostMapping("")
    public ResponseEntity<?> followUser(@RequestBody Map<String, String> request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        User currentUser = (User) authentication.getPrincipal();
        String targetUsername = request.get("username");

        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Username is required");
        }

        try {
            Optional<User> targetUserOpt = userRepository.findByUsername(targetUsername);
            if (targetUserOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            User targetUser = targetUserOpt.get();

            // Can't follow yourself
            if (currentUser.getEmail().equals(targetUser.getEmail())) {
                return ResponseEntity.badRequest().body("Cannot follow yourself");
            }

            // Initialize following list if null
            if (currentUser.getFollowing() == null) {
                currentUser.setFollowing(new HashSet<>());
            }
            if (targetUser.getFollowers() == null) {
                targetUser.setFollowers(new HashSet<>());
            }

            // Check if already following
            if (currentUser.getFollowing().contains(targetUser.getEmail())) {
                return ResponseEntity.badRequest().body("Already following this user");
            }

            // Add to following/followers lists
            currentUser.getFollowing().add(targetUser.getEmail());
            targetUser.getFollowers().add(currentUser.getEmail());

            // Save both users
            userRepository.save(currentUser);
            userRepository.save(targetUser);

            logger.info("✅ {} started following {}", currentUser.getUsername(), targetUser.getUsername());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Successfully followed user");
            response.put("followersCount", targetUser.getFollowers().size());
            response.put("followingCount", currentUser.getFollowing().size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("❌ Follow failed for {} -> {}: {}", currentUser.getUsername(), targetUsername, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to follow user");
        }
    }

    // Unfollow a user
    @DeleteMapping("/{username}")
    public ResponseEntity<?> unfollowUser(@PathVariable String username, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        User currentUser = (User) authentication.getPrincipal();

        try {
            Optional<User> targetUserOpt = userRepository.findByUsername(username);
            if (targetUserOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            User targetUser = targetUserOpt.get();

            // Initialize lists if null
            if (currentUser.getFollowing() == null) {
                currentUser.setFollowing(new HashSet<>());
            }
            if (targetUser.getFollowers() == null) {
                targetUser.setFollowers(new HashSet<>());
            }

            // Check if actually following
            if (!currentUser.getFollowing().contains(targetUser.getEmail())) {
                return ResponseEntity.badRequest().body("Not following this user");
            }

            // Remove from following/followers lists
            currentUser.getFollowing().remove(targetUser.getEmail());
            targetUser.getFollowers().remove(currentUser.getEmail());

            // Save both users
            userRepository.save(currentUser);
            userRepository.save(targetUser);

            logger.info("✅ {} unfollowed {}", currentUser.getUsername(), targetUser.getUsername());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Successfully unfollowed user");
            response.put("followersCount", targetUser.getFollowers().size());
            response.put("followingCount", currentUser.getFollowing().size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("❌ Unfollow failed for {} -> {}: {}", currentUser.getUsername(), username, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to unfollow user");
        }
    }

    // Get follow status
    @GetMapping("/status/{username}")
    public ResponseEntity<?> getFollowStatus(@PathVariable String username, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        User currentUser = (User) authentication.getPrincipal();

        try {
            Optional<User> targetUserOpt = userRepository.findByUsername(username);
            if (targetUserOpt.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("isFollowing", false);
                return ResponseEntity.ok(response);
            }

            User targetUser = targetUserOpt.get();
            boolean isFollowing = currentUser.getFollowing() != null && 
                                currentUser.getFollowing().contains(targetUser.getEmail());

            Map<String, Object> response = new HashMap<>();
            response.put("isFollowing", isFollowing);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("❌ Get follow status failed for {} -> {}: {}", currentUser.getUsername(), username, e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("isFollowing", false);
            return ResponseEntity.ok(response);
        }
    }

    // Get followers of a user
    @GetMapping("/followers/{username}")
    public ResponseEntity<?> getFollowers(@PathVariable String username) {
        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            User user = userOpt.get();
            Set<String> followerEmails = user.getFollowers() != null ? user.getFollowers() : new HashSet<>();

            List<Map<String, Object>> followers = followerEmails.stream()
                .map(email -> {
                    Optional<User> followerOpt = userRepository.findByEmail(email);
                    if (followerOpt.isPresent()) {
                        User follower = followerOpt.get();
                        Map<String, Object> followerData = new HashMap<>();
                        followerData.put("id", follower.getEmail());
                        followerData.put("username", follower.getUsername());
                        followerData.put("email", follower.getEmail());
                        followerData.put("name", follower.getFullName());
                        followerData.put("followersCount", follower.getFollowers() != null ? follower.getFollowers().size() : 0);
                        return followerData;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            return ResponseEntity.ok(followers);

        } catch (Exception e) {
            logger.error("❌ Get followers failed for {}: {}", username, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to get followers");
        }
    }

    // Get users that a user is following
    @GetMapping("/following/{username}")
    public ResponseEntity<?> getFollowing(@PathVariable String username) {
        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            User user = userOpt.get();
            Set<String> followingEmails = user.getFollowing() != null ? user.getFollowing() : new HashSet<>();

            List<Map<String, Object>> following = followingEmails.stream()
                .map(email -> {
                    Optional<User> followingUserOpt = userRepository.findByEmail(email);
                    if (followingUserOpt.isPresent()) {
                        User followingUser = followingUserOpt.get();
                        Map<String, Object> followingData = new HashMap<>();
                        followingData.put("id", followingUser.getEmail());
                        followingData.put("username", followingUser.getUsername());
                        followingData.put("email", followingUser.getEmail());
                        followingData.put("name", followingUser.getFullName());
                        followingData.put("followersCount", followingUser.getFollowers() != null ? followingUser.getFollowers().size() : 0);
                        return followingData;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            return ResponseEntity.ok(following);

        } catch (Exception e) {
            logger.error("❌ Get following failed for {}: {}", username, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to get following");
        }
    }

    // Get follow counts for a user
    @GetMapping("/counts/{username}")
    public ResponseEntity<?> getFollowCounts(@PathVariable String username) {
        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            User user = userOpt.get();
            int followersCount = user.getFollowers() != null ? user.getFollowers().size() : 0;
            int followingCount = user.getFollowing() != null ? user.getFollowing().size() : 0;

            Map<String, Object> response = new HashMap<>();
            response.put("followers", followersCount);
            response.put("following", followingCount);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("❌ Get follow counts failed for {}: {}", username, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to get follow counts");
        }
    }

    // Get suggested users to follow
    @GetMapping("/suggestions")
    public ResponseEntity<?> getSuggestedUsers(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        User currentUser = (User) authentication.getPrincipal();

        try {
            // Get all users except current user and users already being followed
            Set<String> followingEmails = currentUser.getFollowing() != null ? currentUser.getFollowing() : new HashSet<>();
            followingEmails.add(currentUser.getEmail()); // Exclude self

            List<User> allUsers = userRepository.findAll();
            List<Map<String, Object>> suggestions = allUsers.stream()
                .filter(user -> !followingEmails.contains(user.getEmail()))
                .limit(10) // Limit to 10 suggestions
                .map(user -> {
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("id", user.getEmail());
                    userData.put("username", user.getUsername());
                    userData.put("email", user.getEmail());
                    userData.put("name", user.getFullName());
                    userData.put("followersCount", user.getFollowers() != null ? user.getFollowers().size() : 0);
                    return userData;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(suggestions);

        } catch (Exception e) {
            logger.error("❌ Get suggestions failed for {}: {}", currentUser.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to get suggestions");
        }
    }
} 