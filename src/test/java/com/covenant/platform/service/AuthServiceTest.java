package com.covenant.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.covenant.platform.dto.request.LoginRequest;
import com.covenant.platform.dto.request.RegisterRequest;
import com.covenant.platform.dto.response.AuthResponse;
import com.covenant.platform.dto.response.UserResponse;
import com.covenant.platform.entity.User;
import com.covenant.platform.enums.Role;
import com.covenant.platform.repository.UserRepository;
import com.covenant.platform.util.JwtUtils;

/**
 * Unit tests for {@link AuthService}.
 *
 * <p>Uses {@link ExtendWith} with Mockito — no Spring context is spun up.
 * All collaborators are replaced with Mockito doubles, ensuring fast, isolated tests.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    // ─── Mocks ───────────────────────────────────────────────────────────────────

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private AuthService authService;

    // ─── Shared fixtures ──────────────────────────────────────────────────────────

    private static final String EMAIL        = "john.doe@test.com";
    private static final String NAME         = "John Doe";
    private static final String RAW_PASSWORD = "securePass123";
    private static final String ENC_PASSWORD = "$2a$10$hashedValue";
    private static final String JWT_TOKEN    = "header.payload.signature";

    private RegisterRequest validRegisterRequest;
    private LoginRequest    validLoginRequest;
    private User            savedUser;

    @BeforeEach
    void setUp() {
        validRegisterRequest = new RegisterRequest();
        validRegisterRequest.setName(NAME);
        validRegisterRequest.setEmail(EMAIL);
        validRegisterRequest.setPassword(RAW_PASSWORD);

        validLoginRequest = new LoginRequest();
        validLoginRequest.setEmail(EMAIL);
        validLoginRequest.setPassword(RAW_PASSWORD);

        savedUser = new User();
        savedUser.setId("user-001");
        savedUser.setName(NAME);
        savedUser.setEmail(EMAIL);
        savedUser.setPassword(ENC_PASSWORD);
        savedUser.setRole(Role.USER);
        savedUser.setCreatedAt(LocalDateTime.now());
    }

    // ─── register() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("should return UserResponse with correct fields when email is new")
        void register_withNewEmail_shouldReturnUserResponse() {
            // Arrange
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENC_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // Act
            UserResponse response = authService.register(validRegisterRequest);

            // Assert
            assertThat(response.getId()).isEqualTo("user-001");
            assertThat(response.getName()).isEqualTo(NAME);
            assertThat(response.getEmail()).isEqualTo(EMAIL);
            assertThat(response.getRole()).isEqualTo(Role.USER);
            assertThat(response.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw IllegalStateException when email already exists")
        void register_whenEmailAlreadyExists_shouldThrowIllegalStateException() {
            // Arrange
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> authService.register(validRegisterRequest))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Email already in use");

            // Ensure save was never called
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("should encode the raw password before persisting")
        void register_shouldEncodePasswordBeforeSaving() {
            // Arrange
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENC_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // Act
            authService.register(validRegisterRequest);

            // Assert — capture what was actually saved and verify encoded password
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPassword()).isEqualTo(ENC_PASSWORD);
            assertThat(userCaptor.getValue().getPassword()).isNotEqualTo(RAW_PASSWORD);
        }

        @Test
        @DisplayName("should always assign Role.USER regardless of input")
        void register_shouldAlwaysSetRoleToUser() {
            // Arrange
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn(ENC_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // Act
            authService.register(validRegisterRequest);

            // Assert
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("should persist the exact name and email from the request")
        void register_shouldPersistNameAndEmail() {
            // Arrange
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn(ENC_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // Act
            authService.register(validRegisterRequest);

            // Assert
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo(NAME);
            assertThat(captor.getValue().getEmail()).isEqualTo(EMAIL);
        }

        @Test
        @DisplayName("should never expose the password in the returned UserResponse")
        void register_shouldNotExposePasswordInResponse() {
            // Arrange
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn(ENC_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // Act
            UserResponse response = authService.register(validRegisterRequest);

            // Assert — UserResponse has no password field by design;
            // we verify the response object does not carry raw or encoded password
            // by introspecting its declared fields
            boolean hasPasswordField = java.util.Arrays.stream(response.getClass().getDeclaredFields())
                    .anyMatch(f -> f.getName().equalsIgnoreCase("password"));
            assertThat(hasPasswordField).isFalse();
        }
    }

    // ─── login() ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("should return AuthResponse with JWT token on valid credentials")
        void login_withValidCredentials_shouldReturnAuthResponse() {
            // Arrange
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches(RAW_PASSWORD, ENC_PASSWORD)).thenReturn(true);
            when(jwtUtils.generateToken(EMAIL)).thenReturn(JWT_TOKEN);

            // Act
            AuthResponse response = authService.login(validLoginRequest);

            // Assert
            assertThat(response.getToken()).isEqualTo(JWT_TOKEN);
        }

        @Test
        @DisplayName("should throw IllegalStateException when email is not registered")
        void login_whenUserNotFound_shouldThrowIllegalStateException() {
            // Arrange
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> authService.login(validLoginRequest))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        @DisplayName("should throw IllegalStateException when password does not match")
        void login_withWrongPassword_shouldThrowIllegalStateException() {
            // Arrange
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches(RAW_PASSWORD, ENC_PASSWORD)).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> authService.login(validLoginRequest))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        @DisplayName("should call jwtUtils.generateToken with the user's email")
        void login_shouldCallGenerateTokenWithCorrectEmail() {
            // Arrange
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches(RAW_PASSWORD, ENC_PASSWORD)).thenReturn(true);
            when(jwtUtils.generateToken(EMAIL)).thenReturn(JWT_TOKEN);

            // Act
            authService.login(validLoginRequest);

            // Assert
            verify(jwtUtils).generateToken(eq(EMAIL));
        }

        @Test
        @DisplayName("should never generate a token when credentials are invalid")
        void login_withBadCredentials_shouldNeverCallGenerateToken() {
            // Arrange
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> authService.login(validLoginRequest))
                    .isInstanceOf(IllegalStateException.class);

            verify(jwtUtils, never()).generateToken(anyString());
        }
    }
}
