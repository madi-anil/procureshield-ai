package com.procureshield.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vendor_name", nullable = false, length = 120)
    private String vendorName;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_per_seat", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerSeat;

    @Builder.Default
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private String billingCycle = "ANNUAL";

    @Column(name = "logo_r2_url", length = 500)
    private String logoR2Url;

    @Column(name = "soc2_cert_r2_key", length = 255)
    private String soc2CertR2Key;

    @Column(name = "sla_pdf_r2_key", length = 255)
    private String slaPdfR2Key;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "audit_status", nullable = false, length = 30)
    private AuditStatus auditStatus = AuditStatus.PENDING_UPLOAD;

    @Column(name = "audit_started_at")
    private LocalDateTime auditStartedAt;

    @Column(name = "audit_failure_reason", length = 40)
    private String auditFailureReason;

    @Column(name = "ai_risk_score")
    private Integer aiRiskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_risk_level", length = 20)
    private RiskLevel aiRiskLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_risk_scorecard", columnDefinition = "jsonb")
    private Map<String, Object> aiRiskScorecard;

    @Builder.Default
    @Column(name = "renewal_notice_days")
    private Integer renewalNoticeDays = 30;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        validateDatabaseInvariants();
    }

    @PreUpdate
    protected void onUpdate() {
        validateDatabaseInvariants();
    }

    private void validateDatabaseInvariants() {
        if (this.auditStatus == AuditStatus.READY && this.aiRiskScore == null) {
            throw new IllegalStateException("Product cannot be marked READY without an aiRiskScore.");
        }
        if (this.aiRiskScore != null && (this.aiRiskScore < 1 || this.aiRiskScore > 100)) {
            throw new IllegalArgumentException("aiRiskScore must be between 1 and 100.");
        }
    }
}