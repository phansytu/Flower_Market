package com.flowermarketplace.user.dto;

import com.flowermarketplace.common.enums.Role;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long   id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String phoneNumber;
    private Role   role;
    private String profileImageUrl;
    private String city;
    private String bio;
    private boolean enabled;
    private List<AddressDto> addresses;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
