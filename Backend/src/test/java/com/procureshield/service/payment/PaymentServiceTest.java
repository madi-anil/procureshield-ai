package com.procureshield.service.payment;

import com.procureshield.dto.PaymentDtos.PaymentReceiptResponseDto;
import com.procureshield.entity.Department;
import com.procureshield.entity.Payment;
import com.procureshield.entity.PoAuditLog;
import com.procureshield.entity.PurchaseOrder;
import com.procureshield.entity.PurchaseOrderStatus;
import com.procureshield.entity.User;
import com.procureshield.entity.UserRole;
import com.procureshield.repository.DepartmentRepository;
import com.procureshield.repository.PaymentRepository;
import com.procureshield.repository.PoAuditLogRepository;
import com.procureshield.repository.PurchaseOrderRepository;
import com.procureshield.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PoAuditLogRepository poAuditLogRepository;

    private PaymentService paymentService;

    private Department department;
    private User requester;
    private User admin;
    private PurchaseOrder issuedOrder;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository,
                purchaseOrderRepository,
                departmentRepository,
                userRepository,
                poAuditLogRepository
        );

        department = Department.builder()
                .id(1L)
                .name("Platform Engineering")
                .costCenterCode("CC-ENG-101")
                .annualBudget(new BigDecimal("150000.00"))
                .remainingBudget(new BigDecimal("142500.00"))
                .reservedBudget(new BigDecimal("7500.00"))
                .spentBudget(new BigDecimal("0.00"))
                .build();

        requester = User.builder()
                .id(10L)
                .email("emp@procureshield.io")
                .fullName("Anil Employee")
                .role(UserRole.ROLE_EMPLOYEE)
                .departmentId(1L)
                .build();

        admin = User.builder()
                .id(30L)
                .email("admin@procureshield.io")
                .fullName("Admin User")
                .role(UserRole.ROLE_PROCUREMENT_ADMIN)
                .departmentId(1L)
                .build();

        issuedOrder = PurchaseOrder.builder()
                .id(501L)
                .poNumber("PO-2026-0501")
                .requester(requester)
                .department(department)
                .totalAmount(new BigDecimal("7500.00"))
                .status(PurchaseOrderStatus.ISSUED)
                .build();
    }

    @Test
    void settleOrder_movesReservedToSpentAndCompletesPo() {
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(purchaseOrderRepository.findById(501L)).thenReturn(Optional.of(issuedOrder));
        when(paymentRepository.existsByPurchaseOrderId(501L)).thenReturn(false);
        when(departmentRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(department));
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(301L);
            p.setPaidAt(LocalDateTime.now());
            return p;
        });
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(poAuditLogRepository.save(any(PoAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentReceiptResponseDto receipt = paymentService.settleOrder(admin.getEmail(), 501L);

        assertEquals(301L, receipt.paymentId());
        assertEquals("PO-2026-0501", receipt.poNumber());
        assertTrue(receipt.transactionRef().startsWith("TXN-PS-"));
        assertEquals(new BigDecimal("7500.00"), receipt.amountPaid());
        assertEquals(PurchaseOrderStatus.COMPLETED, receipt.poStatus());
        assertEquals(new BigDecimal("142500.00"), receipt.departmentBudget().remaining());
        assertEquals(new BigDecimal("0.00"), receipt.departmentBudget().reserved());
        assertEquals(new BigDecimal("7500.00"), receipt.departmentBudget().spent());
        assertNotNull(receipt.paidAt());
        verify(poAuditLogRepository).save(any(PoAuditLog.class));
    }

    @Test
    void settleOrder_rejectsNonIssuedDuplicateAndSelfRequester() {
        User selfAdmin = User.builder()
                .id(10L)
                .email("emp@procureshield.io")
                .fullName("Anil Employee")
                .role(UserRole.ROLE_PROCUREMENT_ADMIN)
                .departmentId(1L)
                .build();

        when(userRepository.findByEmail(selfAdmin.getEmail())).thenReturn(Optional.of(selfAdmin));
        when(purchaseOrderRepository.findById(501L)).thenReturn(Optional.of(issuedOrder));

        assertThrows(AccessDeniedException.class, () -> paymentService.settleOrder(selfAdmin.getEmail(), 501L));

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(paymentRepository.existsByPurchaseOrderId(501L)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> paymentService.settleOrder(admin.getEmail(), 501L));

        when(paymentRepository.existsByPurchaseOrderId(501L)).thenReturn(false);
        issuedOrder.setStatus(PurchaseOrderStatus.APPROVED);

        assertThrows(IllegalArgumentException.class, () -> paymentService.settleOrder(admin.getEmail(), 501L));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void settleOrder_rejectsInsufficientReservedBudget() {
        department.setReservedBudget(new BigDecimal("2000.00"));

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(purchaseOrderRepository.findById(501L)).thenReturn(Optional.of(issuedOrder));
        when(paymentRepository.existsByPurchaseOrderId(501L)).thenReturn(false);
        when(departmentRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(department));

        assertThrows(IllegalStateException.class, () -> paymentService.settleOrder(admin.getEmail(), 501L));
        verify(paymentRepository, never()).save(any());
    }
}