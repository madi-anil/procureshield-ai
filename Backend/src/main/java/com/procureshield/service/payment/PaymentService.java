package com.procureshield.service.payment;

import com.procureshield.dto.PaymentDtos.DepartmentBudgetSnapshotDto;
import com.procureshield.dto.PaymentDtos.PaymentReceiptResponseDto;
import com.procureshield.entity.Department;
import com.procureshield.entity.Payment;
import com.procureshield.entity.PoAuditLog;
import com.procureshield.entity.PurchaseOrder;
import com.procureshield.entity.PurchaseOrderStatus;
import com.procureshield.entity.User;
import com.procureshield.entity.UserRole;
import com.procureshield.exception.ResourceNotFoundException;
import com.procureshield.repository.DepartmentRepository;
import com.procureshield.repository.PaymentRepository;
import com.procureshield.repository.PoAuditLogRepository;
import com.procureshield.repository.PurchaseOrderRepository;
import com.procureshield.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final PoAuditLogRepository poAuditLogRepository;

    @Transactional
    public PaymentReceiptResponseDto settleOrder(String actorEmail, Long poId) {
        User actor = getUserByEmail(actorEmail);
        if (actor.getRole() != UserRole.ROLE_PROCUREMENT_ADMIN) {
            throw new AccessDeniedException("Only a Procurement Admin can settle purchase orders.");
        }

        PurchaseOrder order = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found with id: " + poId));

        if (Objects.equals(order.getRequester().getId(), actor.getId())) {
            throw new AccessDeniedException("SEPARATION_OF_DUTIES: Requester cannot settle their own purchase order.");
        }

        if (paymentRepository.existsByPurchaseOrderId(order.getId())) {
            throw new IllegalStateException("Purchase order has already been settled.");
        }

        if (order.getStatus() != PurchaseOrderStatus.ISSUED) {
            throw new IllegalArgumentException("Only ISSUED purchase orders can be settled. Current status: " + order.getStatus());
        }

        Department lockedDept = departmentRepository.findByIdWithPessimisticLock(order.getDepartment().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + order.getDepartment().getId()));

        BigDecimal amount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal reserved = lockedDept.getReservedBudget() != null ? lockedDept.getReservedBudget() : BigDecimal.ZERO;
        BigDecimal spent = lockedDept.getSpentBudget() != null ? lockedDept.getSpentBudget() : BigDecimal.ZERO;
        BigDecimal remaining = lockedDept.getRemainingBudget() != null ? lockedDept.getRemainingBudget() : BigDecimal.ZERO;

        if (reserved.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient reserved budget to settle purchase order.");
        }

        lockedDept.setReservedBudget(reserved.subtract(amount));
        lockedDept.setSpentBudget(spent.add(amount));
        departmentRepository.save(lockedDept);

        String txnRef = "TXN-PS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = Payment.builder()
                .purchaseOrder(order)
                .transactionRef(txnRef)
                .amountPaid(amount)
                .paymentMethod("BUDGET_LEDGER")
                .status("COMPLETED")
                .build();
        Payment savedPayment = paymentRepository.save(payment);

        PurchaseOrderStatus previousStatus = order.getStatus();
        order.setStatus(PurchaseOrderStatus.COMPLETED);
        purchaseOrderRepository.save(order);

        PoAuditLog auditLog = PoAuditLog.builder()
                .purchaseOrder(order)
                .actor(actor)
                .previousStatus(previousStatus)
                .newStatus(PurchaseOrderStatus.COMPLETED)
                .comments("Settled invoice under transaction " + txnRef + ".")
                .build();
        poAuditLogRepository.save(auditLog);

        return new PaymentReceiptResponseDto(
                savedPayment.getId(),
                order.getId(),
                order.getPoNumber(),
                savedPayment.getTransactionRef(),
                savedPayment.getAmountPaid(),
                savedPayment.getPaymentMethod(),
                savedPayment.getStatus(),
                new DepartmentBudgetSnapshotDto(
                        remaining,
                        lockedDept.getReservedBudget(),
                        lockedDept.getSpentBudget()
                ),
                order.getStatus(),
                savedPayment.getPaidAt()
        );
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}