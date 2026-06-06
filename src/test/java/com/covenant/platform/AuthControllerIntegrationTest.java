package com.covenant.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.covenant.platform.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String uniqueEmail;

    @BeforeEach
    void setUp() {
        // Generate unique email for each test to avoid conflicts
        uniqueEmail = "testuser-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    @Test
    @DisplayName("Register with valid body should return 200 with user details")
    void register_withValidBody_shouldReturn200() throws Exception {
        Map<String, String> request = Map.of(
                "name", "Test User",
                "email", uniqueEmail,
                "password", "securePassword123"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(uniqueEmail))
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.password").doesNotExist()); // Password should NOT be in response
    }

    @Test
    @DisplayName("Register with missing fields should return 400 with validation errors")
    void register_withMissingFields_shouldReturn400() throws Exception {
        Map<String, String> request = Map.of(
                "name", ""  // Missing email and password, blank name
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("Login with wrong credentials should return 400")
    void login_withWrongCredentials_shouldReturn400() throws Exception {
        Map<String, String> request = Map.of(
                "email", "nonexistent@test.com",
                "password", "wrongPassword"
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()) // IllegalStateException → 400
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }
}
