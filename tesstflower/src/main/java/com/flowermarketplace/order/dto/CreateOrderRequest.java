package com.flowermarketplace.order.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class CreateOrderRequest {

    @NotNull
    private Long listingId;

    @Min(1)
    private int quantity = 1;

    private Long   shippingAddressId;
    private String deliveryType;   // TODAY, TOMORROW, SCHEDULE
    private String note;
}
