package com.covenant.platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.covenant.platform.dto.request.CreateContractRequest;
import com.covenant.platform.dto.request.RaiseDisputeRequest;
import com.covenant.platform.dto.request.ShipContractRequest;
import com.covenant.platform.dto.response.ContractResponse;
import com.covenant.platform.service.ContractService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Tag(name = "Contracts", description = "Endpoints for managing contracts. Some endpoints are specific to Buyers or Sellers.")
public class ContractController {

    private final ContractService contractService;

    @GetMapping
    @Operation(summary = "Get all user contracts", description = "Returns all contracts where the current user is either the buyer or the seller.")
    public ResponseEntity<List<ContractResponse>> getUserContracts() {
        return new ResponseEntity<>(contractService.getUserContracts(), HttpStatus.OK);
    }

    @PostMapping
    @Operation(summary = "Create a contract", description = "Creates a new contract (Seller action). Can optionally provide a buyerEmail to notify the proposed buyer.")
    public ResponseEntity<ContractResponse> createContract(@Valid @RequestBody CreateContractRequest request) {
        return new ResponseEntity<>(contractService.createContract(request), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get contract by ID", description = "View a specific contract (Accessible by Buyer, Seller, or Admin).")
    public ResponseEntity<ContractResponse> getContractById(@PathVariable String id) {
        return new ResponseEntity<>(contractService.getContractById(id), HttpStatus.OK);
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept a contract", description = "Accepts a pending contract and initiates the payment process (Buyer action). Returns the Checkout URL to complete payment.")
    public ResponseEntity<ContractResponse> acceptContract(@PathVariable String id) {
        return new ResponseEntity<>(contractService.acceptContract(id), HttpStatus.OK);
    }

    @PostMapping("/{id}/confirm-payment")
    @Operation(summary = "Confirm payment (Manual)", description = "Manually confirms a payment if webhook fails (Buyer action).")
    public ResponseEntity<ContractResponse> confirmPayment(@PathVariable String id) {
        return ResponseEntity.ok(contractService.confirmPayment(id));
    }

    @PatchMapping("/{id}/ship")
    @Operation(summary = "Ship contract item", description = "Updates tracking details and marks contract as shipped (Seller action).")
    public ResponseEntity<ContractResponse> shipContract(
            @PathVariable String id,
            @Valid @RequestBody ShipContractRequest request) {
        return new ResponseEntity<>(contractService.shipContract(id, request), HttpStatus.OK);
    }

    @PatchMapping("/{id}/delivery")
    @Operation(summary = "Mark as delivered", description = "Marks the item as delivered (Seller action).")
    public ResponseEntity<ContractResponse> markAsDelivered(@PathVariable String id) {
        return new ResponseEntity<>(contractService.markAsDelivered(id), HttpStatus.OK);
    }

    @PostMapping("/{id}/satisfy")
    @Operation(summary = "Mark as satisfied", description = "Confirms satisfaction and releases funds to seller (Buyer action).")
    public ResponseEntity<ContractResponse> markAsSatisfied(@PathVariable String id) {
        return new ResponseEntity<>(contractService.markAsSatisfied(id), HttpStatus.OK);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel contract", description = "Cancels a draft contract (Seller action).")
    public ResponseEntity<ContractResponse> cancelContract(@PathVariable String id) {
        return new ResponseEntity<>(contractService.cancelContract(id), HttpStatus.OK);
    }

    @PostMapping("/{id}/dispute")
    @Operation(summary = "Raise dispute", description = "Raises a dispute on a delivered/shipped item (Buyer action).")
    public ResponseEntity<ContractResponse> raiseDispute(
            @PathVariable String id,
            @Valid @RequestBody RaiseDisputeRequest request) {
        return new ResponseEntity<>(contractService.raiseDispute(id, request), HttpStatus.OK);
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve dispute", description = "Resolves a dispute and releases funds or refunds (Admin action).")
    public ResponseEntity<ContractResponse> resolveDispute(
            @PathVariable String id,
            @RequestParam boolean refundBuyer) {
        return new ResponseEntity<>(contractService.resolveDispute(id, refundBuyer), HttpStatus.OK);
    }
}
