package com.covenant.platform.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.covenant.platform.dto.request.LoginRequest;
import com.covenant.platform.dto.request.RegisterRequest;
import com.covenant.platform.entity.User;
import com.covenant.platform.enums.Role;
import com.covenant.platform.repository.UserRepository;
import com.covenant.platform.util.JwtUtils;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) { 
            throw new RuntimeException("Email already in use");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setRole(Role.USER); 
        
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        return userRepository.save(user);
    }

    public String login(LoginRequest request) {
        // 1. Find User
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Check Password (Raw vs Encrypted)
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        // 3. Generate Token
        return jwtUtils.generateToken(user.getEmail());
    }
}
