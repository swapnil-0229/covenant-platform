package com.covenant.platform.dto.response;

import java.time.LocalDateTime;

import com.covenant.platform.entity.TrackingDetails;
import com.covenant.platform.enums.ContractStatus;
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
public class ContractResponse {
    @Schema(description = "ID of the contract", example = "contractId")
    private String id;
    @Schema(description = "ID of the seller", example = "sellerId")
    private String sellerId;
    @Schema(description = "ID of the buyer", example = "buyerId")
    private String buyerId;
    @Schema(description = "Title of the contract", example = "Contract 1")
    private String title;
    @Schema(description = "Description of the contract", example = "Description 1")
    private String description;
    @Schema(description = "Amount of the contract", example = "100.0")
    private Double amount;
    @Schema(description = "Status of the contract", example = "PENDING")
    private ContractStatus status;
    @Schema(description = "Tracking details of the shipment", example = "TrackingDetails 1")
    private TrackingDetails trackingDetails;

    @Schema(description = "Stripe session ID", example = "stripeSessionId")
    private String stripeSessionId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Created at", example = "2022-01-01T00:00:00")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Updated at", example = "2022-01-01T00:00:00")
    private LocalDateTime updatedAt;

    @Schema(description = "Version of the contract", example = "1")
    private Long version;
}
