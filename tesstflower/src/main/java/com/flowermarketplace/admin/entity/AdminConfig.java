package com.flowermarketplace.admin.entity;

import com.flowermarketplace.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Key-value store for platform-wide configuration that admins
 * can change at runtime without redeploying the application.
 *
 * Examples:
 *   max_listing_images        → "10"
 *   platform_fee_percent      → "5.0"
 *   maintenance_mode          → "false"
 *   featured_listing_limit    → "20"
 *   min_review_order_required → "true"
 */
@Entity
@Table(
    name = "admin_configs",
    indexes = {
        @Index(name = "idx_config_key",      columnList = "config_key", unique = true),
        @Index(name = "idx_config_category", columnList = "category")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AdminConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique machine-readable key. e.g. "platform_fee_percent" */
    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    /** The stored value (always a string; parse on read). */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String configValue;

    /** Human-readable description of what this setting controls. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Groups related settings. e.g. "PAYMENTS", "LISTINGS", "SYSTEM" */
    @Column(length = 50)
    @Builder.Default
    private String category = "SYSTEM";

    /** Value type hint for UI rendering: STRING, INTEGER, DECIMAL, BOOLEAN, JSON */
    @Column(length = 20)
    @Builder.Default
    private String valueType = "STRING";

    /** Whether this value is sensitive (e.g. API keys) — mask in responses. */
    @Builder.Default
    private boolean sensitive = false;

    /** Last admin who changed this config. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
