package com.flowermarketplace.listing.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListingSearchRequest {
    private String     keyword;
    private String     category;
    private String     condition;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String     location;
    private int        page = 0;
    private int        size = 12;
    private String     sort = "createdAt,desc";
}
