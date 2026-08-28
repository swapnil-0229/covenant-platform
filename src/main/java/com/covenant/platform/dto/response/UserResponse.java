package com.covenant.platform.dto.response;

import java.time.LocalDateTime;

import com.covenant.platform.enums.Role;
import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    @Schema(description = "ID of the user", example = "userId")
    private String id;
    @Schema(description = "Name of the user", example = "User 1")
    private String name;
    @Schema(description = "Email of the user", example = "zbc@example.com")
    private String email;
    @Schema(description = "Role of the user", example = "ROLE_SELLER")
    private Role role;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Created at", example = "2022-01-01T00:00:00")
    private LocalDateTime createdAt;
}
