package com.flowermarketplace.user.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String city;
    private String bio;
    private String profileImageUrl;
}
