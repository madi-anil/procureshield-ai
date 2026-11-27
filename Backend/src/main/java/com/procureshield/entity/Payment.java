package com.procureshield.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false, unique = true)
    private PurchaseOrder purchaseOrder;

    @Column(name = "transaction_ref", nullable = false, unique = true, length = 80)
    private String transactionRef;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPaid;

    @Builder.Default
    @Column(name = "payment_method", nullable = false, length = 40)
    private String paymentMethod = "BUDGET_LEDGER";

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "COMPLETED";

    @Column(name = "paid_at", updatable = false)
    private LocalDateTime paidAt;

    @PrePersist
    protected void onCreate() {
        if (this.paidAt == null) {
            this.paidAt = LocalDateTime.now();
        }
        if (this.paymentMethod == null) {
            this.paymentMethod = "BUDGET_LEDGER";
        }
        if (this.status == null) {
            this.status = "COMPLETED";
        }
    }
}