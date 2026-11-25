package com.procureshield.dto;

import java.math.BigDecimal;

public record DepartmentBudgetDto(
        Long id,
        String name,
        String costCenterCode,
        BigDecimal annualBudget,
        BigDecimal remainingBudget,
        BigDecimal reservedBudget,
        BigDecimal spentBudget
) {
}