package com.procureshield.dto;

import com.procureshield.entity.UserRole;

import java.time.LocalDateTime;

public record RegisterResponseDto(
        Long userId,
        String email,
        String fullName,
        UserRole role,
        Long departmentId,
        String departmentName,
        Boolean isActive,
        LocalDateTime createdAt
) {
}