package com.covenant.platform.dto.response;

import java.time.LocalDateTime;

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
public class ErrorResponse {
    @Schema(description = "HTTP status code", example = "400")
    private int status;
    @Schema(description = "Error type", example = "BAD_REQUEST")
    private String error;
    @Schema(description = "Error message", example = "Invalid request")
    private String message;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Timestamp", example = "2022-01-01T00:00:00")
    private LocalDateTime timestamp;
}
