package com.group10.clipnest.controller;

import com.group10.clipnest.model.User;
import com.group10.clipnest.payload.LoginRequest;
import com.group10.clipnest.repository.UserRepository;
import com.group10.clipnest.security.JwtUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.HashSet;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    // Store reset tokens with expiration (in memory for now, should use Redis or DB in production)
    private Map<String, Map<String, Object>> resetTokens = new HashMap<>();

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already exists");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        logger.info("✅ Registered: {}", user);

        // Generate JWT token for the new user
        String token = jwtUtil.generateToken(user);
        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest login) {
        Optional<User> userOpt = userRepository.findByEmail(login.getEmail());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            logger.info("🔓 Attempting login for: {}", user.getUsername());

            if (passwordEncoder.matches(login.getPassword(), user.getPassword())) {
                String token = jwtUtil.generateToken(user);
                logger.info("✅ Login success for: {}", user.getEmail());
                return ResponseEntity.ok(Map.of("token", token));
            } else {
                logger.info("❌ Password mismatch");
            }
        } else {
            logger.info("❌ No user found for email: {}", login.getEmail());
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String email = "Unknown";
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                email = jwtUtil.getEmailFromToken(token);
            } catch (Exception ignored) {}
        }
        String timestamp = LocalDateTime.now().toString();
        System.out.println("User logged out: " + email + " at " + timestamp);
        return ResponseEntity.ok("Logged out");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null) {
            return ResponseEntity.badRequest().body("Email is required");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            // For security, don't reveal if email exists or not
            return ResponseEntity.ok(Map.of("message", "If an account exists with this email, a reset link will be sent."));
        }

        // Generate reset token
        String resetToken = UUID.randomUUID().toString();
        Instant expiration = Instant.now().plusSeconds(3600); // 1 hour expiration

        // Store token with user email and expiration
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("email", email);
        tokenData.put("expiration", expiration);
        resetTokens.put(resetToken, tokenData);

        logger.info("✅ Generated reset token for email: {}", email); // Add logging

        // TODO: Send email with reset link
        // For now, just return the token in the response
        // In production, send this via email and only return a success message
        Map<String, String> response = new HashMap<>();
        response.put("message", "Reset instructions sent");
        response.put("resetToken", resetToken); // Remove this in production
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("newPassword");

        if (token == null || newPassword == null) {
            return ResponseEntity.badRequest().body("Token and new password are required");
        }

        Map<String, Object> resetData = resetTokens.get(token);
        if (resetData == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired reset token");
        }

        Instant expiration = (Instant) resetData.get("expiration");
        if (Instant.now().isAfter(expiration)) {
            resetTokens.remove(token);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Reset token has expired");
        }

        String email = (String) resetData.get("email");
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }

        User user = userOpt.get();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Clean up used token
        resetTokens.remove(token);

        return ResponseEntity.ok("Password reset successfully");
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> body) {
        String idToken = body.get("idToken");
        if (idToken == null) {
            return ResponseEntity.badRequest().body("Missing idToken");
        }

        // Verify the token with Google
        String googleApiUrl = UriComponentsBuilder
            .fromHttpUrl("https://oauth2.googleapis.com/tokeninfo")
            .queryParam("id_token", idToken)
            .toUriString();

        RestTemplate restTemplate = new RestTemplate();
        Map googleResponse;
        try {
            googleResponse = restTemplate.getForObject(googleApiUrl, Map.class);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Google token");
        }

        String email = (String) googleResponse.get("email");
        String name = (String) googleResponse.get("name");
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Google token");
        }

        // Find or create user
        Optional<User> userOpt = userRepository.findByEmail(email);
        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
        } else {
            user = new User();
            user.setEmail(email);
            user.setUsername(name != null ? name : email.split("@")[0]);
            user.setPassword(""); // No password for Google users
            userRepository.save(user);
        }

        String token = jwtUtil.generateToken(user);
        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/direct-reset-password")
    public ResponseEntity<?> directResetPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String newPassword = body.get("newPassword");

        if (email == null || newPassword == null) {
            return ResponseEntity.badRequest().body("Email and new password are required");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User with this email not found");
        }

        User user = userOpt.get();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok("Password reset successfully");
    }

    @PostMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null) {
            return ResponseEntity.badRequest().body("Email is required");
        }
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            return ResponseEntity.ok().body("Email exists");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Email not found");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }
        
        User user = (User) authentication.getPrincipal();
        
        // Expose user data except password
        Map<String, Object> response = new HashMap<>();
        response.put("email", user.getEmail());
        response.put("username", user.getUsername());
        response.put("birthdate", user.getBirthdate());
        response.put("gender", user.getGender());
        response.put("interests", user.getInterests());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/migrate-users")
    public ResponseEntity<?> migrateUsers() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (user.getFollowers() == null) {
                user.setFollowers(new HashSet<>());
            }
            if (user.getFollowing() == null) {
                user.setFollowing(new HashSet<>());
            }
        }
        userRepository.saveAll(users);
        return ResponseEntity.ok("All users have been migrated successfully.");
    }
}