package com.procureshield.repository;

import com.procureshield.entity.PoAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PoAuditLogRepository extends JpaRepository<PoAuditLog, Long> {

    List<PoAuditLog> findByPurchaseOrderIdOrderByCreatedAtAsc(Long purchaseOrderId);
}