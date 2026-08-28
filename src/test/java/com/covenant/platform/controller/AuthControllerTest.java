package com.covenant.platform.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.covenant.platform.dto.request.LoginRequest;
import com.covenant.platform.dto.request.RegisterRequest;
import com.covenant.platform.dto.response.AuthResponse;
import com.covenant.platform.dto.response.UserResponse;
import com.covenant.platform.enums.Role;
import com.covenant.platform.exception.GlobalExceptionHandler;
import com.covenant.platform.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Standalone MockMvc tests for {@link AuthController}.
 *
 * <p>Uses {@link MockMvcBuilders#standaloneSetup} to configure MockMvc with only
 * the controller under test and the {@link GlobalExceptionHandler}, without loading
 * any Spring application context or Security filter chain. This is the most reliable
 * approach for controller-level unit tests because:
 * <ul>
 *   <li>No Spring context = fast startup.</li>
 *   <li>No Security filter chain = no JWT/filter interference.</li>
 *   <li>The {@link GlobalExceptionHandler} is registered as a {@code ControllerAdvice}
 *       so validation errors (→ 400) and service errors (→ 400) are handled correctly.</li>
 * </ul>
 *
 * <p>Every test follows the <em>Arrange–Act–Assert</em> pattern.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Unit Tests (Standalone MockMvc)")
class AuthControllerTest {

    @Mock  private AuthService authService;
    @InjectMocks private AuthController authController;

    private MockMvc     mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // registers JavaTimeModule for LocalDateTime

        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────────────

    private static final String REGISTER_URL = "/api/auth/register";
    private static final String LOGIN_URL    = "/api/auth/login";

    private UserResponse mockUserResponse() {
        return UserResponse.builder()
                .id("user-001")
                .name("John Doe")
                .email("john.doe@test.com")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private AuthResponse mockAuthResponse() {
        return AuthResponse.builder().token("header.payload.signature").build();
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /api/auth/register — Happy path
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("register: valid request body should return 200 with UserResponse")
    void register_withValidBody_shouldReturn200WithUserResponse() throws Exception {
        // Arrange
        RegisterRequest req = new RegisterRequest();
        req.setName("John Doe");
        req.setEmail("john.doe@test.com");
        req.setPassword("securePass123");

        when(authService.register(any(RegisterRequest.class))).thenReturn(mockUserResponse());

        // Act / Assert
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-001"))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john.doe@test.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist()); // sensitive field must NOT appear
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /api/auth/register — Bean-validation failures
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("register: blank name should return 400 mentioning 'name'")
    void register_withBlankName_shouldReturn400() throws Exception {
        // Arrange — blank name violates @NotBlank
        RegisterRequest req = new RegisterRequest();
        req.setName("");
        req.setEmail("john.doe@test.com");
        req.setPassword("securePass123");

        // Act / Assert
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.message", Matchers.containsString("name")));

        verify(authService, never()).register(any());
    }

    @Test
    @DisplayName("register: invalid email format should return 400 mentioning 'email'")
    void register_withInvalidEmailFormat_shouldReturn400() throws Exception {
        // Arrange — violates @Email
        RegisterRequest req = new RegisterRequest();
        req.setName("John Doe");
        req.setEmail("not-a-valid-email");
        req.setPassword("securePass123");

        // Act / Assert
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.message", Matchers.containsString("email")));

        verify(authService, never()).register(any());
    }

    @Test
    @DisplayName("register: password shorter than 6 chars should return 400 mentioning 'password'")
    void register_withShortPassword_shouldReturn400() throws Exception {
        // Arrange — "abc" violates @Size(min=6)
        RegisterRequest req = new RegisterRequest();
        req.setName("John Doe");
        req.setEmail("john.doe@test.com");
        req.setPassword("abc");

        // Act / Assert
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("password")));

        verify(authService, never()).register(any());
    }

    @Test
    @DisplayName("register: empty JSON body should return 400 with Validation Failed")
    void register_withEmptyBody_shouldReturn400() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));

        verify(authService, never()).register(any());
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /api/auth/register — Service-level failure
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("register: duplicate email should return 400 with 'Email already in use' message")
    void register_whenEmailAlreadyExists_shouldReturn400() throws Exception {
        // Arrange
        RegisterRequest req = new RegisterRequest();
        req.setName("John Doe");
        req.setEmail("john.doe@test.com");
        req.setPassword("securePass123");

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new IllegalStateException("Email already in use"));

        // Act / Assert
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Email already in use"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /api/auth/login — Happy path
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("login: valid credentials should return 200 with JWT token")
    void login_withValidCredentials_shouldReturn200WithToken() throws Exception {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setEmail("john.doe@test.com");
        req.setPassword("securePass123");

        when(authService.login(any(LoginRequest.class))).thenReturn(mockAuthResponse());

        // Act / Assert
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("header.payload.signature"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /api/auth/login — Bean-validation failures
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("login: invalid email format should return 400 with Validation Failed")
    void login_withInvalidEmailFormat_shouldReturn400() throws Exception {
        // Arrange — violates @Email
        LoginRequest req = new LoginRequest();
        req.setEmail("bad-email-format");
        req.setPassword("securePass123");

        // Act / Assert
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));

        verify(authService, never()).login(any());
    }

    @Test
    @DisplayName("login: blank password should return 400 with Validation Failed")
    void login_withBlankPassword_shouldReturn400() throws Exception {
        // Arrange — violates @NotBlank
        LoginRequest req = new LoginRequest();
        req.setEmail("john.doe@test.com");
        req.setPassword("");

        // Act / Assert
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(authService, never()).login(any());
    }

    @Test
    @DisplayName("login: empty JSON body should return 400 with Validation Failed")
    void login_withEmptyBody_shouldReturn400() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(authService, never()).login(any());
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /api/auth/login — Service-level failure
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("login: wrong credentials should return 400 with 'Invalid email or password' message")
    void login_withWrongCredentials_shouldReturn400WithMessage() throws Exception {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setEmail("john.doe@test.com");
        req.setPassword("wrongPassword");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new IllegalStateException("Invalid email or password"));

        // Act / Assert
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }
}
