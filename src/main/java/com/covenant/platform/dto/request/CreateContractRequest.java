package com.covenant.platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateContractRequest {

    @NotBlank(message = "Title is required")
    @Schema(description = "Title of the contract", example = "Contract 1")
    private String title;

    @NotBlank(message = "Description is required")
    @Schema(description = "Description of the contract", example = "Description 1")
    private String description;

    @Min(value = 1, message = "Amount must be greater than 0")
    @Schema(description = "Amount of the contract", example = "100.0")
    private Double amount;

    @Email(message = "Please provide a valid email address")
    @Schema(description = "Buyer's email address", example = "zbc@example.com")
    private String buyerEmail;
}
