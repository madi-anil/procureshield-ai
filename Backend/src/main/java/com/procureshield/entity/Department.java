package com.procureshield.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "cost_center_code", nullable = false, unique = true, length = 30)
    private String costCenterCode;

    @Column(name = "annual_budget", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal annualBudget = BigDecimal.ZERO;

    @Column(name = "remaining_budget", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal remainingBudget = BigDecimal.ZERO;

    @Column(name = "reserved_budget", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal reservedBudget = BigDecimal.ZERO;

    @Column(name = "spent_budget", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal spentBudget = BigDecimal.ZERO;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}