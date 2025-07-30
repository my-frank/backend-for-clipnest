package com.group10.clipnest.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashSet;
import java.util.Set;

@Document(collection = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    private String id;
    private String email;
    private String username;
    private String password;
    private String birthdate;
    private String gender;
    private java.util.List<String> interests;
    private String fullName;
    private Set<String> followers = new HashSet<>();  // Set of user IDs of users who follow this user
    private Set<String> following = new HashSet<>();  // Set of user IDs of users this user follows

}
