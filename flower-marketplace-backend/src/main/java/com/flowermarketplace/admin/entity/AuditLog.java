package com.flowermarketplace.admin.entity;

import com.flowermarketplace.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Immutable record of every action performed by an admin.
 * Written once, never updated.
 */
@Entity
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_admin",      columnList = "admin_id"),
        @Index(name = "idx_audit_action",     columnList = "action"),
        @Index(name = "idx_audit_entity",     columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_created_at", columnList = "created_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The admin who performed the action. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    /**
     * Short verb describing what happened.
     * Examples: USER_ROLE_CHANGED, LISTING_ARCHIVED, ORDER_STATUS_UPDATED,
     *           REVIEW_DEACTIVATED, USER_BANNED, CONFIG_UPDATED, REFUND_ISSUED
     */
    @Column(nullable = false, length = 100)
    private String action;

    /** Domain object type that was affected. e.g. USER, LISTING, ORDER, PAYMENT, REVIEW */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    /** Primary key of the affected domain object. */
    @Column(name = "entity_id")
    private Long entityId;

    /**
     * Human-readable description of the change.
     * e.g. "Changed role from ROLE_BUYER to ROLE_SELLER"
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Optional JSON snapshot of the entity state before the change. */
    @Column(name = "before_value", columnDefinition = "TEXT")
    private String beforeValue;

    /** Optional JSON snapshot of the entity state after the change. */
    @Column(name = "after_value", columnDefinition = "TEXT")
    private String afterValue;

    /** IP address of the admin's request. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
