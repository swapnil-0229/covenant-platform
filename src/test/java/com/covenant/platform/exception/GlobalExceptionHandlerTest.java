package com.covenant.platform.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import com.covenant.platform.dto.response.ErrorResponse;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>The handler is instantiated directly — no HTTP layer or Spring context needed.
 * Each test invokes the relevant {@code @ExceptionHandler} method and asserts on
 * the returned {@link ResponseEntity}.
 */
@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ─── ResourceNotFoundException → 404 ─────────────────────────────────────────

    @Test
    @DisplayName("should return 404 with error body for ResourceNotFoundException")
    void handleResourceNotFoundException_shouldReturn404() {
        // Arrange
        ResourceNotFoundException ex =
                new ResourceNotFoundException("Contract", "id", "abc-123");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFoundException(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(404);
        assertThat(body.getError()).isEqualTo("Not Found");
        assertThat(body.getMessage()).contains("Contract").contains("id").contains("abc-123");
        assertThat(body.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("should return 404 with single-arg ResourceNotFoundException message")
    void handleResourceNotFoundException_withSingleArgCtor_shouldReturn404() {
        // Arrange
        ResourceNotFoundException ex = new ResourceNotFoundException("Generic resource not found");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFoundException(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Generic resource not found");
    }

    // ─── IllegalStateException → 400 ─────────────────────────────────────────────

    @Test
    @DisplayName("should return 400 with error body for IllegalStateException")
    void handleIllegalStateException_shouldReturn400() {
        // Arrange
        IllegalStateException ex = new IllegalStateException("Email already in use");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleIllegalStateException(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getError()).isEqualTo("Bad Request");
        assertThat(body.getMessage()).isEqualTo("Email already in use");
        assertThat(body.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("should preserve the original IllegalStateException message in the response body")
    void handleIllegalStateException_shouldPreserveMessage() {
        // Arrange
        String msg = "Cannot cancel. Contract can only be cancelled in DRAFT status";
        IllegalStateException ex = new IllegalStateException(msg);

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleIllegalStateException(ex);

        // Assert
        assertThat(response.getBody().getMessage()).isEqualTo(msg);
    }

    // ─── AccessDeniedException → 403 ─────────────────────────────────────────────

    @Test
    @DisplayName("should return 403 with error body for AccessDeniedException")
    void handleAccessDeniedException_shouldReturn403() {
        // Arrange
        AccessDeniedException ex = new AccessDeniedException("Access is denied");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleAccessDeniedException(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(403);
        assertThat(body.getError()).isEqualTo("Forbidden");
        assertThat(body.getMessage()).isEqualTo("Access is denied");
        assertThat(body.getTimestamp()).isNotNull();
    }

    // ─── OptimisticLockingFailureException → 409 ─────────────────────────────────

    @Test
    @DisplayName("should return 409 with generic conflict message for OptimisticLockingFailureException")
    void handleOptimisticLockingFailure_shouldReturn409() {
        // Arrange
        OptimisticLockingFailureException ex =
                new OptimisticLockingFailureException("Concurrent modification");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockingFailure(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(409);
        assertThat(body.getError()).isEqualTo("Conflict");
        // Message is intentionally a generic safe message (not the raw exception message)
        assertThat(body.getMessage()).contains("modified by another request");
        assertThat(body.getTimestamp()).isNotNull();
    }

    // ─── Generic Exception → 500 ─────────────────────────────────────────────────

    @Test
    @DisplayName("should return 500 with generic message for unexpected RuntimeException")
    void handleGenericException_withRuntimeException_shouldReturn500() {
        // Arrange
        RuntimeException ex = new RuntimeException("Database connection lost");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getError()).isEqualTo("Internal Server Error");
        // The raw exception message must NOT be exposed to clients
        assertThat(body.getMessage()).doesNotContain("Database connection lost");
        assertThat(body.getMessage()).isEqualTo("An unexpected error occurred. Please try again later.");
        assertThat(body.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("should return 500 for checked Exception subtype")
    void handleGenericException_withCheckedException_shouldReturn500() {
        // Arrange
        Exception ex = new Exception("Some checked exception");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getStatus()).isEqualTo(500);
    }
}
