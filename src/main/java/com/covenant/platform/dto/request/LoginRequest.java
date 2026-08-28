package com.covenant.platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @Email(message = "Invalid email format")
    @Schema(description = "User's email address", example = "zbc@example.com")
    @NotBlank(message = "Email is required")
    private String email;

    @Schema(description = "User's password", example = "securePassword123")
    @NotBlank(message = "Password is required")
    private String password;
}
