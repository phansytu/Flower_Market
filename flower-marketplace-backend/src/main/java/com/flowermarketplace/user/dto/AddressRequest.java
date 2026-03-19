package com.flowermarketplace.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressRequest {

    @NotBlank
    private String fullName;

    @NotBlank
    private String phoneNumber;

    @NotBlank
    private String addressLine;

    private String ward;
    private String district;

    @NotBlank
    private String city;

    @Builder.Default
    private String country = "VN";

    private boolean isDefault;
}
