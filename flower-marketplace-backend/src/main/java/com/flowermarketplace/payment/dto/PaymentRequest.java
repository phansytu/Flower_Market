package com.flowermarketplace.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod;   // CARD | PAYPAL | STRIPE | COD

    /**
     * Tokenised payment instrument from client-side SDK (e.g. Stripe.js token).
     * Not required for COD.
     */
    private String paymentToken;

    /** Optional client-supplied idempotency key. */
    private String idempotencyKey;
}
