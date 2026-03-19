package com.flowermarketplace.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingStatsDto {
    private Long targetId;         // listingId or sellerId
    private String targetType;     // "LISTING" or "SELLER"
    private double averageRating;
    private long totalReviews;
    private Map<Integer, Long> distribution; // star → count (1..5)
}
