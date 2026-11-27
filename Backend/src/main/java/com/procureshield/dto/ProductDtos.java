package com.procureshield.dto;

import com.procureshield.entity.AuditStatus;
import com.procureshield.entity.RiskLevel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record CreateProductRequestDto(
            @NotBlank(message = "Vendor name is required")
            @Size(max = 120)
            String vendorName,

            @NotBlank(message = "Product name is required")
            @Size(max = 120)
            String productName,

            @NotBlank(message = "Category is required")
            @Size(max = 60)
            String category,

            String description,

            @NotNull(message = "Price per seat is required")
            @DecimalMin(value = "0.00", inclusive = true)
            @Digits(integer = 10, fraction = 2)
            BigDecimal pricePerSeat,

            @Size(max = 20)
            String billingCycle,

            @Positive
            Integer renewalNoticeDays
    ) {
    }

    public record UpdateProductRequestDto(
            @NotBlank(message = "Vendor name is required")
            @Size(max = 120)
            String vendorName,

            @NotBlank(message = "Product name is required")
            @Size(max = 120)
            String productName,

            @NotBlank(message = "Category is required")
            @Size(max = 60)
            String category,

            String description,

            @NotNull(message = "Price per seat is required")
            @DecimalMin(value = "0.00", inclusive = true)
            @Digits(integer = 10, fraction = 2)
            BigDecimal pricePerSeat,

            @Size(max = 20)
            String billingCycle,

            @Positive
            Integer renewalNoticeDays,

            Boolean isActive
    ) {
    }

    public record ProductResponseDto(
            Long id,
            String vendorName,
            String productName,
            String category,
            String description,
            BigDecimal pricePerSeat,
            String billingCycle,
            String logoR2Url,
            String soc2CertR2Key,
            String slaPdfR2Key,
            AuditStatus auditStatus,
            String auditFailureReason,
            Integer aiRiskScore,
            RiskLevel aiRiskLevel,
            Integer renewalNoticeDays,
            Boolean isActive,
            LocalDateTime createdAt
    ) {
    }

    public record SlaUploadResponseDto(
            Long productId,
            AuditStatus auditStatus,
            String slaPdfR2Key,
            String message
    ) {
    }

    public record AssetUploadResponseDto(
            Long productId,
            String type,
            String r2Key,
            String url
    ) {
    }

    public record RiskScorecardResponseDto(
            Long productId,
            String productName,
            String vendorName,
            AuditStatus auditStatus,
            String auditFailureReason,
            Integer aiRiskScore,
            RiskLevel aiRiskLevel,
            Map<String, Object> scorecard
    ) {
    }
}