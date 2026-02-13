package com.covenant.platform.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.covenant.platform.entity.User;

public interface UserRepository extends MongoRepository<User, String>{
    Optional<User> findByEmail(String email);
    
    // Check if email exists during Registration
    boolean existsByEmail(String email);
}
