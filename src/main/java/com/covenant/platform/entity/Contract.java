package com.covenant.platform.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import com.covenant.platform.enums.ContractStatus;

import lombok.Data;

@Data
@Document
public class Contract {
    @Id
    private String id; 

    // parties
    private String sellerId;
    private String buyerId; 

    // deal details
    private String title;   
    private String description;
    private Double amount; 

    // state machine
    private ContractStatus status;

    // logistics
    private TrackingDetails trackingDetails;

    // payment tracking
    private String paymentIntentId; // Stripe PaymentIntent ID for tracking

    // optimistic locking (prevents concurrent modifications)
    @Version
    private Long version;

    // audit timestamps
    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
