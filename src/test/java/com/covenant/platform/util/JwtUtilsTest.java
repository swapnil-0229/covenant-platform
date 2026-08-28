package com.covenant.platform.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link JwtUtils}.
 *
 * <p>{@link JwtUtils} relies on a Base64-encoded secret injected via {@code @Value}.
 * We use {@link ReflectionTestUtils} to set the secret field directly and call
 * {@link JwtUtils#init()} to mimic {@code @PostConstruct} — no Spring context needed.
 *
 * <p>The test secret is a randomly generated 256-bit (32-byte) Base64 string that
 * satisfies the HMAC-SHA256 key-length requirement.
 */
@DisplayName("JwtUtils Unit Tests")
class JwtUtilsTest {

    /**
     * A valid Base64-encoded 256-bit key used exclusively for testing.
     * Generated via: Base64.getEncoder().encodeToString(new byte[32])
     */
    private static final String TEST_SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RpbmctcHVycG9zZXMh";

    private static final String EMAIL = "user@covenant.com";

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", TEST_SECRET);
        jwtUtils.init(); // simulate @PostConstruct
    }

    // ─── generateToken() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("generateToken()")
    class GenerateToken {

        @Test
        @DisplayName("should produce a non-blank JWT string")
        void generateToken_shouldProduceNonBlankToken() {
            // Act
            String token = jwtUtils.generateToken(EMAIL);

            // Assert
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("should produce a token containing three dot-separated segments (header.payload.signature)")
        void generateToken_shouldHaveThreeJwtSegments() {
            // Act
            String token = jwtUtils.generateToken(EMAIL);

            // Assert
            String[] parts = token.split("\\.");
            assertThat(parts).hasSize(3);
        }

        @Test
        @DisplayName("should produce different tokens on successive calls (due to iat claim)")
        void generateToken_calledTwice_shouldProduceDifferentTokens() throws InterruptedException {
            // Act — small sleep to ensure different issued-at timestamps
            String token1 = jwtUtils.generateToken(EMAIL);
            Thread.sleep(1001);
            String token2 = jwtUtils.generateToken(EMAIL);

            // Assert
            assertThat(token1).isNotEqualTo(token2);
        }
    }

    // ─── extractUsername() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("extractUsername()")
    class ExtractUsername {

        @Test
        @DisplayName("should return the exact email embedded in the token subject claim")
        void extractUsername_shouldReturnCorrectEmail() {
            // Given
            String token = jwtUtils.generateToken(EMAIL);

            // Act
            String extracted = jwtUtils.extractUsername(token);

            // Assert
            assertThat(extracted).isEqualTo(EMAIL);
        }

        @Test
        @DisplayName("should work correctly for any valid email format")
        void extractUsername_withDifferentEmails_shouldReturnCorrectly() {
            // Given
            String specialEmail = "another.user+tag@sub.domain.io";
            String token = jwtUtils.generateToken(specialEmail);

            // Act
            String extracted = jwtUtils.extractUsername(token);

            // Assert
            assertThat(extracted).isEqualTo(specialEmail);
        }

        @Test
        @DisplayName("should throw an exception when the token is tampered with")
        void extractUsername_withTamperedToken_shouldThrow() {
            // Given
            String token = jwtUtils.generateToken(EMAIL);
            // Corrupt the signature segment
            String[] parts  = token.split("\\.");
            String tampered = parts[0] + "." + parts[1] + ".invalidsignature";

            // Act & Assert
            assertThatThrownBy(() -> jwtUtils.extractUsername(tampered))
                    .isInstanceOf(Exception.class); // io.jsonwebtoken.security.SignatureException
        }
    }

    // ─── validateToken() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateToken()")
    class ValidateToken {

        @Test
        @DisplayName("should return true when token is valid and email matches")
        void validateToken_withCorrectEmail_shouldReturnTrue() {
            // Given
            String token = jwtUtils.generateToken(EMAIL);

            // Act
            boolean valid = jwtUtils.validateToken(token, EMAIL);

            // Assert
            assertThat(valid).isTrue();
        }

        @Test
        @DisplayName("should return false when email does not match the token subject")
        void validateToken_withWrongEmail_shouldReturnFalse() {
            // Given
            String token = jwtUtils.generateToken(EMAIL);

            // Act
            boolean valid = jwtUtils.validateToken(token, "other@covenant.com");

            // Assert
            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("should throw an exception for a completely malformed token string")
        void validateToken_withMalformedToken_shouldThrow() {
            // Given
            String malformed = "this.is.notavalidjwt";

            // Act & Assert
            assertThatThrownBy(() -> jwtUtils.validateToken(malformed, EMAIL))
                    .isInstanceOf(Exception.class);
        }
    }
}
