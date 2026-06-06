package com.covenant.platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PaymentService {
    
    @Value("${stripe.key.secret}")
    private String secretKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey; // Initialize Stripe with your Secret Key
    }

    public String createPaymentIntent(Double amount) throws StripeException {
        PaymentIntent intent = createPaymentIntentInternal(amount, null, null);
        return intent.getClientSecret();
    }

    public PaymentIntent createPaymentIntentInternal(Double amount, String contractId, String buyerId) throws StripeException {
        // Stripe requires the amount in "cents" (or paise) as a Long
        // e.g., ₹10.00 becomes 1000 paise
        long amountInPaise = (long) (amount * 100);
        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountInPaise)
                .setCurrency("inr") //Must be lowercase 'inr'
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build()
                );

        // Link PaymentIntent to contract via metadata (for webhook processing)
        if (contractId != null) {
            paramsBuilder.putMetadata("contractId", contractId);
        }

        PaymentIntentCreateParams params = paramsBuilder.build();

        // Build request options with idempotency key to prevent duplicate charges
        if (contractId != null && buyerId != null) {
            String idempotencyKey = "contract-" + contractId + "-" + buyerId;
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            log.info("Creating PaymentIntent with idempotency key: {}", idempotencyKey);
            return PaymentIntent.create(params, options);
        }

        // Call Stripe API without idempotency key (fallback)
        return PaymentIntent.create(params);
    }

    public PaymentIntent getPaymentIntent(String paymentIntentId) throws StripeException {
        return PaymentIntent.retrieve(paymentIntentId);
    }
}
