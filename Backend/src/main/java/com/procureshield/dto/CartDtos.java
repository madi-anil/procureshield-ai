package com.procureshield.dto;

import com.procureshield.entity.AuditStatus;
import com.procureshield.entity.RiskLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    public record AddCartItemRequestDto(
            @NotNull(message = "Product ID is required")
            Long productId,

            @NotNull(message = "Seat count is required")
            @Positive(message = "Seat count must be greater than 0")
            Integer seats
    ) {
    }

    public record CartItemResponseDto(
            Long id,
            Long productId,
            String productName,
            String vendorName,
            String category,
            String billingCycle,
            BigDecimal pricePerSeat,
            Integer seats,
            BigDecimal lineTotal,
            AuditStatus auditStatus,
            Integer aiRiskScore,
            RiskLevel aiRiskLevel,
            boolean highRiskItem
    ) {
    }

    public record CartSummaryResponseDto(
            List<CartItemResponseDto> items,
            int totalItems,
            int totalSeats,
            BigDecimal estimatedTotalCost,
            Long departmentId,
            String departmentName,
            BigDecimal departmentRemainingBudget,
            boolean exceedsRemainingBudget,
            boolean highRiskWarning,
            int highestRiskScore,
            List<Long> highRiskProductIds
    ) {
    }
}