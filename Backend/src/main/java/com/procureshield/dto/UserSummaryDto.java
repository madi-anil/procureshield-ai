package com.procureshield.dto;

import com.procureshield.entity.UserRole;

public record UserSummaryDto(
        Long id,
        String email,
        String fullName,
        UserRole role,
        Long departmentId,
        String departmentName
) {
}