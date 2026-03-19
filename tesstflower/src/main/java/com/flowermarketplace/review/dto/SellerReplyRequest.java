package com.flowermarketplace.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SellerReplyRequest {

    @NotBlank(message = "Reply content is required")
    @Size(max = 500, message = "Reply cannot exceed 500 characters")
    private String reply;
}
