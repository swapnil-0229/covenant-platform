package com.covenant.platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RaiseDisputeRequest {
    @Schema(description = "Reason for the dispute", example = "Dispute 1")
    @NotBlank(message = "Dispute reason is required")
    private String reason;
}
