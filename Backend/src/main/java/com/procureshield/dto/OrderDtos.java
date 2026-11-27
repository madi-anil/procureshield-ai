package com.procureshield.dto;

import com.procureshield.entity.PurchaseOrderStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class OrderDtos {

    public enum OrderTransitionStatus {
        APPROVED,
        REJECTED,
        ISSUED
    }

    public record CheckoutRequestDto(
            @NotBlank(message = "Justification is required.")
            String justification,

            @NotNull(message = "Contract end date is required.")
            @Future(message = "Contract end date must be in the future.")
            LocalDate contractEndDate
    ) {
    }

    public record OrderStatusUpdateDto(
            @NotNull(message = "New status is required.")
            OrderTransitionStatus newStatus,

            String comments
    ) {
    }

    public record OrderLineItemDto(
            Long productId,
            String productName,
            Integer seatQuantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
    }

    public record OrderAuditLogDto(
            Long id,
            String actorName,
            PurchaseOrderStatus previousStatus,
            PurchaseOrderStatus newStatus,
            String comments,
            LocalDateTime createdAt
    ) {
    }

    public record CheckoutResponseDto(
            Long poId,
            String poNumber,
            PurchaseOrderStatus status,
            BigDecimal totalAmount,
            Integer highestAiRiskScore,
            Boolean requiresEscalation,
            Long departmentId,
            Long requesterId,
            LocalDate contractEndDate,
            LocalDateTime createdAt
    ) {
    }

    public record OrderStatusResponseDto(
            Long poId,
            String poNumber,
            PurchaseOrderStatus previousStatus,
            PurchaseOrderStatus currentStatus,
            String reviewedBy,
            Long auditLogId,
            LocalDateTime updatedAt
    ) {
    }

    public record OrderDetailResponseDto(
            Long poId,
            String poNumber,
            PurchaseOrderStatus status,
            BigDecimal totalAmount,
            Integer highestAiRiskScore,
            Boolean requiresEscalation,
            String requesterName,
            String departmentName,
            String justification,
            LocalDate contractEndDate,
            List<OrderLineItemDto> items,
            List<OrderAuditLogDto> auditLogs,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}