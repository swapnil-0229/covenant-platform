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

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @PostMapping
    public ResponseEntity<ContractResponse> createContract(@Valid @RequestBody CreateContractRequest request) {
        return new ResponseEntity<>(contractService.createContract(request), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContractResponse> getContractById(@PathVariable String id) {
        return new ResponseEntity<>(contractService.getContractById(id), HttpStatus.OK);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ContractResponse> acceptContract(@PathVariable String id) {
        return new ResponseEntity<>(contractService.acceptContract(id), HttpStatus.OK);
    }

    @PostMapping("/{id}/confirm-payment")
    public ResponseEntity<ContractResponse> confirmPayment(@PathVariable String id) {
        return ResponseEntity.ok(contractService.confirmPayment(id));
    }

    @PatchMapping("/{id}/ship")
    public ResponseEntity<ContractResponse> shipContract(
            @PathVariable String id,
            @Valid @RequestBody ShipContractRequest request) {
        return new ResponseEntity<>(contractService.shipContract(id, request), HttpStatus.OK);
    }

    @PatchMapping("/{id}/delivery")
    public ResponseEntity<ContractResponse> markAsDelivered(@PathVariable String id) {
        return new ResponseEntity<>(contractService.markAsDelivered(id), HttpStatus.OK);
    }

    @PostMapping("/{id}/satisfy")
    public ResponseEntity<ContractResponse> markAsSatisfied(@PathVariable String id) {
        return new ResponseEntity<>(contractService.markAsSatisfied(id), HttpStatus.OK);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ContractResponse> cancelContract(@PathVariable String id) {
        return new ResponseEntity<>(contractService.cancelContract(id), HttpStatus.OK);
    }

    @PostMapping("/{id}/dispute")
    public ResponseEntity<ContractResponse> raiseDispute(
            @PathVariable String id,
            @Valid @RequestBody RaiseDisputeRequest request) {
        return new ResponseEntity<>(contractService.raiseDispute(id, request), HttpStatus.OK);
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<ContractResponse> resolveDispute(
            @PathVariable String id,
            @RequestParam boolean refundBuyer) {
        return new ResponseEntity<>(contractService.resolveDispute(id, refundBuyer), HttpStatus.OK);
    }
}
