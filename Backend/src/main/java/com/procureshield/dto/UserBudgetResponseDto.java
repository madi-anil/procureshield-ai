package com.procureshield.dto;

import com.procureshield.entity.UserRole;


public record UserBudgetResponseDto (
        Long userId,
        String email,
        String fullName,
        UserRole role,
        DepartmentBudgetDto department
){
}
