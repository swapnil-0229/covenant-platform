package com.covenant.platform.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.covenant.platform.enums.Role;

import lombok.Data;

@Data
@Document(collection = "users")
public class User {
    @Id
    private String id; 

    private String name;

    @Indexed(unique = true) 
    private String email;

    private String password; 

    private Role role = Role.USER; 

    private LocalDateTime createdAt = LocalDateTime.now();

}
