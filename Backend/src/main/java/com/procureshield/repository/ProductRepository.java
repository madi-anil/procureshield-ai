package com.procureshield.repository;

import com.procureshield.entity.AuditStatus;
import com.procureshield.entity.Product;
import com.procureshield.entity.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndIsActiveTrue(Long id);

    @Query("""
            SELECT p FROM Product p
            WHERE p.isActive = true
              AND (:category IS NULL OR LOWER(p.category) = LOWER(:category))
              AND (:riskLevel IS NULL OR p.aiRiskLevel = :riskLevel)
              AND (:auditStatus IS NULL OR p.auditStatus = :auditStatus)
              AND (:search IS NULL OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :search, '%'))
                                  OR LOWER(p.vendorName) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Product> searchActiveCatalog(
            @Param("category") String category,
            @Param("riskLevel") RiskLevel riskLevel,
            @Param("auditStatus") AuditStatus auditStatus,
            @Param("search") String search,
            Pageable pageable
    );

    List<Product> findByAuditStatusAndAuditStartedAtBefore(AuditStatus auditStatus, LocalDateTime cutoff);
}