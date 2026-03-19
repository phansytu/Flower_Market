package com.flowermarketplace.auth.dto;

import com.flowermarketplace.common.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank @Email
    private String email;

    private String phoneNumber;

    @NotBlank @Size(min = 6)
    private String password;

    private Role role = Role.ROLE_BUYER;
}
