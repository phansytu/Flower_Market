package com.flowermarketplace.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stub payment-gateway service.
 * Replace the charge / refund methods with real Stripe / PayPal SDK calls.
 */
@Slf4j
@Service
public class PaymentGatewayService {

    /**
     * Charge the customer via the external gateway.
     *
     * @param token          client-side payment token (Stripe token, PayPal order-id, etc.)
     * @param amount         amount to charge
     * @param idempotencyKey unique key preventing duplicate charges
     * @return external transaction ID on success, null on failure
     */
    public String charge(String token, BigDecimal amount, String idempotencyKey) {
        log.info("[Gateway] Charging {} with token={} idempotencyKey={}", amount, token, idempotencyKey);
        // TODO: replace with Stripe charge:
        //   Stripe.apiKey = stripeSecret;
        //   PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
        //       .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
        //       .setCurrency("usd")
        //       .setPaymentMethod(token)
        //       .setConfirm(true)
        //       .setIdempotencyKey(idempotencyKey)
        //       .build();
        //   PaymentIntent intent = PaymentIntent.create(params);
        //   return intent.getId();

        // Simulate: always succeeds unless token == "fail"
        if ("fail".equalsIgnoreCase(token)) return null;
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    /**
     * Issue a full or partial refund.
     *
     * @param transactionId  original gateway transaction ID
     * @param amount         amount to refund
     * @return refund transaction ID on success, null on failure
     */
    public String refund(String transactionId, BigDecimal amount) {
        log.info("[Gateway] Refunding {} for transactionId={}", amount, transactionId);
        // TODO: replace with Stripe refund:
        //   RefundCreateParams params = RefundCreateParams.builder()
        //       .setPaymentIntent(transactionId)
        //       .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
        //       .build();
        //   Refund refund = Refund.create(params);
        //   return refund.getId();

        return "RFD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
