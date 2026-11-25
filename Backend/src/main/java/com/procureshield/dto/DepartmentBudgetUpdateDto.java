package com.procureshield.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DepartmentBudgetUpdateDto(
        @NotNull(message = "Annual budget is required")
        @DecimalMin(value = "0.00", inclusive = true, message = "Annual budget must be at least 0.00")
        @Digits(integer = 12, fraction = 2, message = "Annual budget must have at most 2 decimal places")
        BigDecimal annualBudget
) {
}