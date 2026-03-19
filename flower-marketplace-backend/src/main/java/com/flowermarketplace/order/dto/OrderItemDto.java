package com.flowermarketplace.order.dto;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderItemDto {
    private Long       id;
    private Long       listingId;
    private String     listingTitle;
    private String     listingImageUrl;
    private int        quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
