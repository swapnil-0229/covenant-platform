package com.covenant.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShipContractRequest {
    @NotBlank(message = "Tracking ID is required")
    private String trackingId;

    @NotBlank(message = "Provider name is required")
    private String logisticsProvider; 
}
