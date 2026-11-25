package com.procureshield.dto;

import com.procureshield.entity.UserRole;

public record UserRoleResponseDto(
        Long userId,
        String email,
        UserRole role,
        boolean refreshTokensRevoked
) {
}