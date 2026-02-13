package com.covenant.platform.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.covenant.platform.service.ContractService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/webhooks/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {
    
    private final ContractService contractService;
    
    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    @PostMapping
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        
        Event event;
        try {
            // Verify webhook signature (important for security)
            if (webhookSecret != null && !webhookSecret.isEmpty() && sigHeader != null) {
                event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            } else {
                log.warn("Stripe webhook secret not configured or signature missing. " +
                         "Processing event without verification (NOT RECOMMENDED FOR PRODUCTION).");
                // For development/testing: parse event without verification
                // In production, always require webhook secret
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Webhook secret must be configured for security");
            }
        } catch (SignatureVerificationException e) {
            log.error("Webhook signature verification failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            log.error("Error parsing webhook event", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error parsing event");
        }

        // Handle the event
        if ("payment_intent.succeeded".equals(event.getType())) {
            PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                    .getObject().orElse(null);
            
            if (paymentIntent != null) {
                String contractId = paymentIntent.getMetadata().get("contractId");
                
                if (contractId != null) {
                    try {
                        log.info("Processing payment confirmation for contract: {}", contractId);
                        contractService.confirmPaymentInternal(
                            contractService.getContractByIdInternal(contractId)
                        );
                        log.info("Payment confirmed successfully for contract: {}", contractId);
                    } catch (Exception e) {
                        log.error("Error confirming payment for contract {}: {}", contractId, e.getMessage(), e);
                        // Return 200 to prevent Stripe from retrying (or 500 to trigger retry)
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Error processing payment confirmation");
                    }
                } else {
                    log.warn("PaymentIntent {} has no contractId metadata", paymentIntent.getId());
                }
            }
        } else {
            log.debug("Unhandled event type: {}", event.getType());
        }

        return ResponseEntity.ok("Webhook processed");
    }
}
