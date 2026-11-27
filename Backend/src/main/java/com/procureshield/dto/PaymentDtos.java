package com.procureshield.dto;

import com.procureshield.entity.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentDtos {

    public record DepartmentBudgetSnapshotDto(
            BigDecimal remaining,
            BigDecimal reserved,
            BigDecimal spent
    ) {
    }

    public record PaymentReceiptResponseDto(
            Long paymentId,
            Long poId,
            String poNumber,
            String transactionRef,
            BigDecimal amountPaid,
            String paymentMethod,
            String status,
            DepartmentBudgetSnapshotDto departmentBudget,
            PurchaseOrderStatus poStatus,
            LocalDateTime paidAt
    ) {
    }
}