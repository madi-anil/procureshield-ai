package com.procureshield.dto;

import com.procureshield.entity.AuditStatus;
import com.procureshield.entity.RiskLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class WishlistDtos {

    public record AddWishlistItemRequestDto(
            @NotNull(message = "Product ID is required.")
            Long productId
    ) {
    }

    public record MoveToCartRequestDto(
            @Min(value = 1, message = "Seat count must be at least 1.")
            Integer seats
    ) {
    }

    public record WishlistItemResponseDto(
            Long id,
            Long productId,
            String productName,
            String vendorName,
            String category,
            BigDecimal pricePerSeat,
            AuditStatus auditStatus,
            Integer aiRiskScore,
            RiskLevel aiRiskLevel,
            LocalDateTime savedAt
    ) {
    }

    public record WishlistResponseDto(
            List<WishlistItemResponseDto> items,
            int totalCount
    ) {
    }
}