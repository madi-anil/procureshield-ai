package com.procureshield.service.order;

import com.procureshield.dto.OrderDtos.CheckoutRequestDto;
import com.procureshield.dto.OrderDtos.CheckoutResponseDto;
import com.procureshield.dto.OrderDtos.OrderStatusResponseDto;
import com.procureshield.dto.OrderDtos.OrderStatusUpdateDto;
import com.procureshield.dto.OrderDtos.OrderTransitionStatus;
import com.procureshield.service.notification.NotificationService;
import com.procureshield.entity.AuditStatus;
import com.procureshield.entity.CartItem;
import com.procureshield.entity.Department;
import com.procureshield.entity.PoAuditLog;
import com.procureshield.entity.Product;
import com.procureshield.entity.PurchaseOrder;
import com.procureshield.entity.PurchaseOrderStatus;
import com.procureshield.entity.User;
import com.procureshield.entity.UserRole;
import com.procureshield.repository.CartItemRepository;
import com.procureshield.repository.DepartmentRepository;
import com.procureshield.repository.PoAuditLogRepository;
import com.procureshield.repository.PurchaseOrderRepository;
import com.procureshield.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PoAuditLogRepository poAuditLogRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private NotificationService notificationService;

    private OrderService orderService;

    private Department department;
    private User employee;
    private User deptHead;
    private User admin;
    private Product readyProduct;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                purchaseOrderRepository,
                poAuditLogRepository,
                cartItemRepository,
                userRepository,
                departmentRepository,
                notificationService
        );
        ReflectionTestUtils.setField(orderService, "escalationThreshold", new BigDecimal("10000.00"));

        department = Department.builder()
                .id(1L)
                .name("Platform Engineering")
                .costCenterCode("CC-ENG-101")
                .annualBudget(new BigDecimal("150000.00"))
                .remainingBudget(new BigDecimal("20000.00"))
                .reservedBudget(new BigDecimal("0.00"))
                .spentBudget(new BigDecimal("0.00"))
                .build();

        employee = User.builder()
                .id(10L)
                .email("emp@procureshield.io")
                .fullName("Anil Employee")
                .role(UserRole.ROLE_EMPLOYEE)
                .departmentId(1L)
                .build();

        deptHead = User.builder()
                .id(20L)
                .email("head@procureshield.io")
                .fullName("Vikram Head")
                .role(UserRole.ROLE_DEPARTMENT_HEAD)
                .departmentId(1L)
                .build();

        admin = User.builder()
                .id(30L)
                .email("admin@procureshield.io")
                .fullName("Admin User")
                .role(UserRole.ROLE_PROCUREMENT_ADMIN)
                .departmentId(1L)
                .build();

        readyProduct = Product.builder()
                .id(101L)
                .productName("Datadog Enterprise APM")
                .vendorName("Datadog")
                .pricePerSeat(new BigDecimal("300.00"))
                .auditStatus(AuditStatus.READY)
                .aiRiskScore(54)
                .isActive(true)
                .build();
    }

    @Test
    void checkout_convertsCartToPendingPoAndClearsCart() {
        CartItem item = CartItem.builder()
                .id(1L)
                .user(employee)
                .product(readyProduct)
                .seats(25)
                .build();

        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(cartItemRepository.findByUserIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(item));
        when(purchaseOrderRepository.count()).thenReturn(0L);
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> {
            PurchaseOrder po = inv.getArgument(0);
            po.setId(501L);
            return po;
        });

        CheckoutResponseDto response = orderService.checkout(
                employee.getEmail(),
                new CheckoutRequestDto("Observability tooling for K8s.", LocalDate.now().plusMonths(6))
        );

        assertEquals(501L, response.poId());
        assertEquals(PurchaseOrderStatus.PENDING_APPROVAL, response.status());
        assertEquals(new BigDecimal("7500.00"), response.totalAmount());
        assertEquals(54, response.highestAiRiskScore());
        assertFalse(response.requiresEscalation());
        verify(cartItemRepository).deleteAll(List.of(item));
    }

    @Test
    void checkout_rejectsEmptyCartAndNonReadyProduct() {
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(cartItemRepository.findByUserIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () ->
                orderService.checkout(
                        employee.getEmail(),
                        new CheckoutRequestDto("Valid justification text", LocalDate.now().plusDays(30))
                )
        );

        Product auditingProduct = Product.builder()
                .id(102L)
                .productName("Unaudited Tool")
                .pricePerSeat(new BigDecimal("100.00"))
                .auditStatus(AuditStatus.AUDITING)
                .isActive(true)
                .build();
        CartItem unauditedItem = CartItem.builder().id(2L).user(employee).product(auditingProduct).seats(2).build();
        when(cartItemRepository.findByUserIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(unauditedItem));

        assertThrows(IllegalStateException.class, () ->
                orderService.checkout(
                        employee.getEmail(),
                        new CheckoutRequestDto("Valid justification text", LocalDate.now().plusDays(30))
                )
        );
    }

    @Test
    void approveOrder_reservesBudgetAndWritesAuditLog() {
        PurchaseOrder order = PurchaseOrder.builder()
                .id(501L)
                .poNumber("PO-2026-0501")
                .requester(employee)
                .department(department)
                .totalAmount(new BigDecimal("7500.00"))
                .requiresEscalation(false)
                .status(PurchaseOrderStatus.PENDING_APPROVAL)
                .auditLogs(new ArrayList<>())
                .build();

        when(userRepository.findByEmail(deptHead.getEmail())).thenReturn(Optional.of(deptHead));
        when(purchaseOrderRepository.findById(501L)).thenReturn(Optional.of(order));
        when(departmentRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(department));
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));
        when(poAuditLogRepository.save(any(PoAuditLog.class))).thenAnswer(inv -> {
            PoAuditLog log = inv.getArgument(0);
            log.setId(9001L);
            return log;
        });
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderStatusResponseDto result = orderService.updateStatus(
                deptHead.getEmail(),
                501L,
                new OrderStatusUpdateDto(OrderTransitionStatus.APPROVED, "Approved under budget.")
        );

        assertEquals(PurchaseOrderStatus.PENDING_APPROVAL, result.previousStatus());
        assertEquals(PurchaseOrderStatus.APPROVED, result.currentStatus());
        assertEquals(9001L, result.auditLogId());
        assertEquals(new BigDecimal("12500.00"), department.getRemainingBudget());
        assertEquals(new BigDecimal("7500.00"), department.getReservedBudget());
    }

    @Test
    void approveOrder_enforcesSeparationOfDutiesEscalationAndBudget() {
        PurchaseOrder selfOrder = PurchaseOrder.builder()
                .id(502L)
                .poNumber("PO-2026-0502")
                .requester(deptHead)
                .department(department)
                .totalAmount(new BigDecimal("2000.00"))
                .requiresEscalation(false)
                .status(PurchaseOrderStatus.PENDING_APPROVAL)
                .auditLogs(new ArrayList<>())
                .build();

        when(userRepository.findByEmail(deptHead.getEmail())).thenReturn(Optional.of(deptHead));
        when(purchaseOrderRepository.findById(502L)).thenReturn(Optional.of(selfOrder));

        assertThrows(AccessDeniedException.class, () ->
                orderService.updateStatus(
                        deptHead.getEmail(),
                        502L,
                        new OrderStatusUpdateDto(OrderTransitionStatus.APPROVED, "Self approve")
                )
        );

        PurchaseOrder escalatedOrder = PurchaseOrder.builder()
                .id(503L)
                .poNumber("PO-2026-0503")
                .requester(employee)
                .department(department)
                .totalAmount(new BigDecimal("15000.00"))
                .requiresEscalation(true)
                .status(PurchaseOrderStatus.PENDING_APPROVAL)
                .auditLogs(new ArrayList<>())
                .build();

        when(purchaseOrderRepository.findById(503L)).thenReturn(Optional.of(escalatedOrder));

        assertThrows(AccessDeniedException.class, () ->
                orderService.updateStatus(
                        deptHead.getEmail(),
                        503L,
                        new OrderStatusUpdateDto(OrderTransitionStatus.APPROVED, "Dept head trying escalated")
                )
        );

        PurchaseOrder overBudgetOrder = PurchaseOrder.builder()
                .id(504L)
                .poNumber("PO-2026-0504")
                .requester(employee)
                .department(department)
                .totalAmount(new BigDecimal("25000.00"))
                .requiresEscalation(true)
                .status(PurchaseOrderStatus.PENDING_APPROVAL)
                .auditLogs(new ArrayList<>())
                .build();

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(purchaseOrderRepository.findById(504L)).thenReturn(Optional.of(overBudgetOrder));
        when(departmentRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(department));

        assertThrows(IllegalArgumentException.class, () ->
                orderService.updateStatus(
                        admin.getEmail(),
                        504L,
                        new OrderStatusUpdateDto(OrderTransitionStatus.APPROVED, "Over budget")
                )
        );
        verify(poAuditLogRepository, never()).save(any());
    }

    @Test
    void rejectRequiresCommentsAndAdminCanIssueApprovedOrder() {
        PurchaseOrder pending = PurchaseOrder.builder()
                .id(505L)
                .poNumber("PO-2026-0505")
                .requester(employee)
                .department(department)
                .totalAmount(new BigDecimal("2000.00"))
                .status(PurchaseOrderStatus.PENDING_APPROVAL)
                .auditLogs(new ArrayList<>())
                .build();

        when(userRepository.findByEmail(deptHead.getEmail())).thenReturn(Optional.of(deptHead));
        when(purchaseOrderRepository.findById(505L)).thenReturn(Optional.of(pending));

        assertThrows(IllegalArgumentException.class, () ->
                orderService.updateStatus(
                        deptHead.getEmail(),
                        505L,
                        new OrderStatusUpdateDto(OrderTransitionStatus.REJECTED, " ")
                )
        );

        PurchaseOrder approved = PurchaseOrder.builder()
                .id(506L)
                .poNumber("PO-2026-0506")
                .requester(employee)
                .department(department)
                .totalAmount(new BigDecimal("2000.00"))
                .status(PurchaseOrderStatus.APPROVED)
                .auditLogs(new ArrayList<>())
                .build();

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(purchaseOrderRepository.findById(506L)).thenReturn(Optional.of(approved));
        when(poAuditLogRepository.save(any(PoAuditLog.class))).thenAnswer(inv -> {
            PoAuditLog log = inv.getArgument(0);
            log.setId(9002L);
            return log;
        });
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderStatusResponseDto issued = orderService.updateStatus(
                admin.getEmail(),
                506L,
                new OrderStatusUpdateDto(OrderTransitionStatus.ISSUED, "Sent to vendor.")
        );

        assertEquals(PurchaseOrderStatus.APPROVED, issued.previousStatus());
        assertEquals(PurchaseOrderStatus.ISSUED, issued.currentStatus());
        assertTrue(issued.reviewedBy().contains("Admin User"));
        assertNotNull(issued.auditLogId());
    }
}