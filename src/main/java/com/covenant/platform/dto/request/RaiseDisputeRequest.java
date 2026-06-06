package com.covenant.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RaiseDisputeRequest {
    @NotBlank(message = "Dispute reason is required")
    private String reason;
}
