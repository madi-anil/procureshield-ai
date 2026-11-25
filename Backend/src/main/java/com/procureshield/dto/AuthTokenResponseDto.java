package com.procureshield.dto;

public record AuthTokenResponseDto(
        String tokenType,
        String accessToken,
        String refreshToken,
        Long expiresInSeconds,
        UserSummaryDto user
) {
}