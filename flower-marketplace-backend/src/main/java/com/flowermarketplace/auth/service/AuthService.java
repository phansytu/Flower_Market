package com.flowermarketplace.auth.service;

import com.flowermarketplace.auth.dto.AuthResponse;
import com.flowermarketplace.auth.dto.LoginRequest;
import com.flowermarketplace.auth.dto.RefreshTokenRequest;
import com.flowermarketplace.auth.dto.RegisterRequest;
import com.flowermarketplace.auth.entity.RefreshToken;
import com.flowermarketplace.auth.repository.RefreshTokenRepository;
import com.flowermarketplace.common.enums.Role;
import com.flowermarketplace.common.exception.BadRequestException;
import com.flowermarketplace.common.exception.UnauthorizedException;
import com.flowermarketplace.common.security.JwtUtil;
import com.flowermarketplace.user.entity.User;
import com.flowermarketplace.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpirationMs;

    // ── Register ──────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered.");
        }
        if (request.getPhoneNumber() != null
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new BadRequestException("Phone number already in use.");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_BUYER)
                .enabled(true)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {} ({})", user.getEmail(), user.getRole());

        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = generateAndSaveRefreshToken(user);

        return buildResponse(user, accessToken, refreshToken);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Email nhận được: {}", request.getEmail());
        log.info("Mật khẩu nhận được: {}", request.getPassword()); // Cẩn thận khi log pass ở môi trường thật
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials."));

        // Revoke previous refresh tokens
        refreshTokenRepository.revokeAllByUserId(user.getId());

        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = generateAndSaveRefreshToken(user);

        log.info("User logged in: {}", user.getEmail());
        return buildResponse(user, accessToken, refreshToken);
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken rt = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token."));

        if (!rt.isValid()) {
            throw new UnauthorizedException("Refresh token expired or revoked.");
        }

        User user = rt.getUser();
        rt.setRevoked(true);
        refreshTokenRepository.save(rt);

        String newAccess = jwtUtil.generateToken(user);
        String newRefresh = generateAndSaveRefreshToken(user);

        return buildResponse(user, newAccess, newRefresh);
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Transactional
    public void logout(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            refreshTokenRepository.revokeAllByUserId(user.getId());
            log.info("User logged out: {}", email);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String generateAndSaveRefreshToken(User user) {
        String token = UUID.randomUUID().toString();
        RefreshToken rt = RefreshToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .revoked(false)
                .build();
        refreshTokenRepository.save(rt);
        return token;
    }

    private AuthResponse buildResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .profileImageUrl(user.getProfileImageUrl())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
