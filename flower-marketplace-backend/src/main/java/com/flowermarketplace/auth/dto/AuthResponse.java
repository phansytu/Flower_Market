package com.flowermarketplace.auth.dto;

import com.flowermarketplace.common.enums.Role;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private Role role;
    private String profileImageUrl;
    private String accessToken;
    private String refreshToken;
}
