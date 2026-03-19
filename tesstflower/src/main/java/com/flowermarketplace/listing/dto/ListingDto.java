package com.flowermarketplace.listing.dto;

import com.flowermarketplace.common.enums.ListingStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingDto {
    private Long          id;
    private Long          sellerId;
    private String        sellerName;
    private String        sellerAvatar;
    private String        sellerCity;
    private String        title;
    private String        description;
    private String        category;
    private String        condition;
    private BigDecimal    price;
    private int           stockQuantity;
    private String        location;
    private String        tags;
    private List<String>  imageUrls;
    private ListingStatus status;
    private boolean       freeDelivery;
    private double        averageRating;
    private int           reviewCount;
    private int           viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
