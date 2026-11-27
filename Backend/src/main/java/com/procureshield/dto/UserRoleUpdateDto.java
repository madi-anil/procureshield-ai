package com.procureshield.dto;

import com.procureshield.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateDto(
        @NotNull(message = "Role is required")
        UserRole role
) {
}