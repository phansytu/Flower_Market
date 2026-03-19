package com.flowermarketplace.listing.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateListingRequest {

    @NotBlank
    private String title;

    private String description;

    @NotBlank
    private String category;

    @NotBlank
    private String condition;

    @NotNull @DecimalMin("0.01")
    private BigDecimal price;

    @Min(1)
    private int stockQuantity = 1;

    private String location;

    private String tags;

    private boolean freeDelivery = false;
}
