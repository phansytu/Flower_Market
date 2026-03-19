package com.flowermarketplace.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDto {
    private Long id;
    private Long reviewerId;
    private String reviewerName;
    private String reviewerAvatar;
    private Long listingId;
    private String listingTitle;
    private Long sellerId;
    private String sellerName;
    private int rating;
    private String comment;
    private String sellerReply;
    private LocalDateTime sellerRepliedAt;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
