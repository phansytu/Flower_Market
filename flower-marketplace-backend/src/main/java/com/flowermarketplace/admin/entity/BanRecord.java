package com.flowermarketplace.admin.entity;

import com.flowermarketplace.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Records a ban or suspension applied to a user account.
 * A user may have multiple ban records (ban → unban → ban again).
 */
@Entity
@Table(
    name = "ban_records",
    indexes = {
        @Index(name = "idx_ban_user",      columnList = "user_id"),
        @Index(name = "idx_ban_admin",     columnList = "banned_by"),
        @Index(name = "idx_ban_active",    columnList = "active"),
        @Index(name = "idx_ban_expires",   columnList = "expires_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class BanRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who was banned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The admin who issued the ban. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banned_by", nullable = false)
    private User bannedBy;

    /** Why the user was banned. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    /**
     * Ban type: PERMANENT or TEMPORARY.
     * If TEMPORARY, expiresAt must be set.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BanType banType = BanType.PERMANENT;

    /** Null for permanent bans; non-null for temporary suspensions. */
    private LocalDateTime expiresAt;

    /** Whether this ban record is still active. */
    @Builder.Default
    private boolean active = true;

    /** The admin who lifted the ban (null if not yet lifted). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lifted_by")
    private User liftedBy;

    private LocalDateTime liftedAt;

    @Column(columnDefinition = "TEXT")
    private String liftReason;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    public enum BanType {
        PERMANENT, TEMPORARY
    }

    /** Convenience: has a temporary ban already expired? */
    public boolean isExpired() {
        return banType == BanType.TEMPORARY
                && expiresAt != null
                && LocalDateTime.now().isAfter(expiresAt);
    }
}
