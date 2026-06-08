package com.covenant.platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

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

    public Session createCheckoutSession(Double amount, String contractId, String buyerId, String title) throws StripeException {
        long amountInPaise = (long) (amount * 100);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("https://covenant-api-mo93.onrender.com/swagger-ui/index.html")
                .setCancelUrl("https://covenant-api-mo93.onrender.com/swagger-ui/index.html")
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("inr")
                                                .setUnitAmount(amountInPaise)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(title != null ? title : "Contract Payment")
                                                                .build())
                                                .build())
                                .build())
                .setPaymentIntentData(
                        SessionCreateParams.PaymentIntentData.builder()
                                .putMetadata("contractId", contractId)
                                .build())
                .build();

        if (contractId != null && buyerId != null) {
            String idempotencyKey = "checkout-" + contractId + "-" + buyerId;
            com.stripe.net.RequestOptions options = com.stripe.net.RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            return Session.create(params, options);
        }

        return Session.create(params);
    }

    public PaymentIntent getPaymentIntent(String paymentIntentId) throws StripeException {
        return PaymentIntent.retrieve(paymentIntentId);
    }
}
