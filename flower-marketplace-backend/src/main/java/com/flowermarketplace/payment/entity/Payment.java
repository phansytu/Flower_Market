package com.flowermarketplace.payment.entity;

import com.flowermarketplace.common.enums.PaymentStatus;
import com.flowermarketplace.order.entity.Order;
import com.flowermarketplace.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payment_order",  columnList = "order_id"),
        @Index(name = "idx_payment_user",   columnList = "user_id"),
        @Index(name = "idx_payment_status", columnList = "status"),
        @Index(name = "idx_payment_txn",    columnList = "transaction_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    /** CARD, PAYPAL, STRIPE, COD … */
    @Column(nullable = false)
    private String paymentMethod;

    /** External gateway transaction / charge ID */
    @Column(name = "transaction_id", unique = true)
    private String transactionId;

    /** Raw JSON / message from gateway (truncated for storage) */
    @Column(columnDefinition = "TEXT")
    private String gatewayResponse;

    /** Idempotency key sent to gateway to prevent double-charges */
    @Column(unique = true)
    private String idempotencyKey;

    /** Non-null when a refund has been issued */
    private String refundTransactionId;
    private LocalDateTime refundedAt;

    @Column(precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
