package com.flowermarketplace.notification.entity;

import com.flowermarketplace.common.enums.NotificationType;
import com.flowermarketplace.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notif_user",   columnList = "user_id"),
        @Index(name = "idx_notif_read",   columnList = "is_read"),
        @Index(name = "idx_notif_type",   columnList = "type"),
        @Index(name = "idx_notif_created",columnList = "created_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Optional deep-link reference (orderId, listingId, etc.) */
    private String referenceId;

    /** e.g. "ORDER", "LISTING", "REVIEW" */
    private String referenceType;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    private LocalDateTime readAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
