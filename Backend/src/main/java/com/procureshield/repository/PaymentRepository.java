package com.procureshield.repository;

import com.procureshield.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByPurchaseOrderId(Long purchaseOrderId);

    Optional<Payment> findByPurchaseOrderId(Long purchaseOrderId);
}