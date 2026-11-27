package com.procureshield.repository;

import com.procureshield.entity.PurchaseOrder;
import com.procureshield.entity.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    List<PurchaseOrder> findAllByOrderByCreatedAtDesc();

    List<PurchaseOrder> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    List<PurchaseOrder> findByDepartmentIdOrderByCreatedAtDesc(Long departmentId);

    Page<PurchaseOrder> findByStatusOrderByCreatedAtDesc(PurchaseOrderStatus status, Pageable pageable);

    long count();
}