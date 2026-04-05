package com.flowermarketplace.common.config;

import com.flowermarketplace.common.enums.Role;
import com.flowermarketplace.user.entity.User;
import com.flowermarketplace.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initAdmin() {
        return args -> {
            System.out.println("🔥 CommandLineRunner đang chạy...");

            String email = "admin@gmail.com";

            if (userRepository.findByEmail(email).isEmpty()) {
                System.out.println("👉 Chưa có admin, tạo mới...");

                User admin = User.builder()
                        .firstName("Admin")
                        .lastName("Tu")
                        .email(email)
                        .phoneNumber("0968941503")
                        .password(passwordEncoder.encode("240805"))
                        .role(Role.ROLE_ADMIN)
                        .enabled(true)
                        .build();

                userRepository.save(admin);

                System.out.println("✅ ADMIN CREATED");
            } else {
                System.out.println("⚡ ADMIN ĐÃ TỒN TẠI");
            }
        };
    }

}
