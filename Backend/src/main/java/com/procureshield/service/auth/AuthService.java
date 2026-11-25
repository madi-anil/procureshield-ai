package com.procureshield.service.auth;

import com.procureshield.dto.AuthTokenResponseDto;
import com.procureshield.dto.LoginRequestDto;
import com.procureshield.dto.RefreshTokenRequestDto;
import com.procureshield.dto.RegisterRequestDto;
import com.procureshield.dto.RegisterResponseDto;
import com.procureshield.dto.TokenRefreshResponseDto;
import com.procureshield.dto.UserSummaryDto;
import com.procureshield.entity.RefreshToken;
import com.procureshield.entity.User;
import com.procureshield.entity.UserRole;
import com.procureshield.exception.DuplicateResourceException;
import com.procureshield.exception.InvalidTokenException;
import com.procureshield.repository.RefreshTokenRepository;
import com.procureshield.repository.UserRepository;
import com.procureshield.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${procureshield.jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    @Transactional
    public RegisterResponseDto register(RegisterRequestDto request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .departmentId(request.departmentId())
                .role(UserRole.ROLE_EMPLOYEE)
                .isActive(true)
                .build();

        User saved = userRepository.save(user);
        log.info("Registered new employee userId={}", saved.getId());

        return new RegisterResponseDto(
                saved.getId(),
                saved.getEmail(),
                saved.getFullName(),
                saved.getRole(),
                saved.getDepartmentId(),
                null,
                saved.getIsActive(),
                saved.getCreatedAt()
        );
    }

    @Transactional
    public AuthTokenResponseDto login(LoginRequestDto request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidTokenException("Invalid email or password"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new InvalidTokenException("Account is inactive");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidTokenException("Invalid email or password");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String rawRefreshToken = issueRefreshToken(user.getId());

        UserSummaryDto summary = new UserSummaryDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getDepartmentId(),
                null
        );

        return new AuthTokenResponseDto(
                "Bearer",
                accessToken,
                rawRefreshToken,
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                summary
        );
    }

    @Transactional
    public TokenRefreshResponseDto refresh(RefreshTokenRequestDto request) {
        String providedHash = hashToken(request.refreshToken().trim());

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(providedHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (Boolean.TRUE.equals(storedToken.getRevoked())) {
            throw new InvalidTokenException("Refresh token has been revoked");
        }

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Refresh token has expired");
        }

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new InvalidTokenException("User not found for refresh token"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new InvalidTokenException("Account is inactive");
        }

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = issueRefreshToken(user.getId());

        return new TokenRefreshResponseDto(
                newAccessToken,
                newRefreshToken,
                jwtTokenProvider.getAccessTokenExpirationSeconds()
        );
    }

    private String issueRefreshToken(Long userId) {
        String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
        long expirationSeconds = Math.max(1L, refreshTokenExpirationMs / 1000L);

        RefreshToken tokenEntity = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hashToken(rawToken))
                .expiresAt(LocalDateTime.now().plusSeconds(expirationSeconds))
                .revoked(false)
                .build();

        refreshTokenRepository.save(tokenEntity);
        return rawToken;
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}