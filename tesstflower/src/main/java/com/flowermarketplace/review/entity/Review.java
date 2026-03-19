package com.flowermarketplace.review.entity;

import com.flowermarketplace.listing.entity.Listing;
import com.flowermarketplace.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "reviews",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_reviewer_listing",
        columnNames = {"reviewer_id", "listing_id"}
    ),
    indexes = {
        @Index(name = "idx_review_listing", columnList = "listing_id"),
        @Index(name = "idx_review_seller",  columnList = "seller_id"),
        @Index(name = "idx_review_reviewer", columnList = "reviewer_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    /** 1–5 stars */
    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    /** Seller's public reply to this review */
    @Column(columnDefinition = "TEXT")
    private String sellerReply;

    private LocalDateTime sellerRepliedAt;

    /** Soft-delete / moderation flag */
    @Builder.Default
    private boolean active = true;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
