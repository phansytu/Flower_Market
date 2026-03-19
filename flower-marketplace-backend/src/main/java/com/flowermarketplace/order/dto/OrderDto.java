package com.flowermarketplace.order.dto;

import com.flowermarketplace.common.enums.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderDto {
    private Long          id;
    private String        orderNumber;
    private Long          buyerId;
    private String        buyerName;
    private List<OrderItemDto> items;
    private OrderStatus   status;
    private BigDecimal    totalAmount;
    private String        shippingAddressLine;
    private String        deliveryType;
    private String        note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
