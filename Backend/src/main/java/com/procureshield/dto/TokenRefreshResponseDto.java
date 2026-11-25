package com.procureshield.dto;

public record TokenRefreshResponseDto(
        String accessToken,
        String refreshToken,
        Long expiresInSeconds
) {
}