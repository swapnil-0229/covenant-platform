package com.covenant.platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShipContractRequest {
    @NotBlank(message = "Tracking ID is required")
    @Schema(description = "Tracking ID of the shipment", example = "123456789")
    private String trackingId;

    @NotBlank(message = "Provider name is required")
    @Schema(description = "Provider name of the shipment", example = "Provider 1")
    private String logisticsProvider;
}
