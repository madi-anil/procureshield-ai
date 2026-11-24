# 09. TESTING STRATEGY & TEST SUITES

## 1. Testing Matrix
- **Unit Testing:** JUnit 5 + Mockito (Spring Boot domain services); Vitest + React Testing Library (React 18+ UI components).
- **Integration Testing:** `@SpringBootTest` with PostgreSQL 16 / Testcontainers & MockMvc (Flyway migrations, RBAC filters, and `po_audit_logs` immutability).
- **API Testing:** Exported Postman Collection (`postman/ProcureShield_AI.postman_collection.json`) executing end-to-end endpoint validations.

## 2. Sample JUnit 5 Unit Test (Order Service)

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PoAuditLogRepository poAuditLogRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void whenValidApprovalWithinBudget_thenApproveAndAppendAuditLog() {
        // Given
                Department dept = Department.builder()
                .id(1L)
                .annualBudget(new BigDecimal("10000.00"))
                .remainingBudget(new BigDecimal("10000.00"))
                .reservedBudget(BigDecimal.ZERO)
                .spentBudget(BigDecimal.ZERO)
                .build();

        PurchaseOrder order = PurchaseOrder.builder()
                .id(501L)
                .poNumber("PO-2026-0501")
                .department(dept)
                .totalAmount(new BigDecimal("7500.00"))
                .status(OrderStatus.PENDING_APPROVAL)
                .build();

        when(purchaseOrderRepository.findById(501L)).thenReturn(Optional.of(order));
        when(departmentRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(dept));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(poAuditLogRepository.save(any(PoAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        OrderStatusResponseDto result = orderService.approveOrder(501L, 2L, "Approved under Q4 budget.");

        // Then
        assertNotNull(result);
        assertEquals("APPROVED", result.currentStatus());
        assertEquals(new BigDecimal("2500.00"), dept.getRemainingBudget());
        assertEquals(new BigDecimal("7500.00"), dept.getReservedBudget());
        verify(poAuditLogRepository, times(1)).save(any(PoAuditLog.class));
    }
}

```

## 3. Postman Collection Verification Checklist

* `POST /api/v1/auth/login` -> Expect `200 OK` with JWT `accessToken` and `refreshToken`.
* `POST /api/v1/products/{id}/upload-sla` -> Expect `202 Accepted` with `auditStatus: "AUDITING"`.
* `GET /api/v1/products/{id}/risk-scorecard` -> Expect `200 OK` with `overallScore` and `riskLevel`.
* `POST /api/v1/cart/items` -> Expect `200 OK` with seat subtotal and `highRiskWarning` flag.
* `POST /api/v1/orders/checkout` -> Expect `201 Created` with `poNumber` and `PENDING_APPROVAL` status.
* `PATCH /api/v1/orders/{poId}/status` -> Expect `200 OK` with updated status and audit log entry.
* `POST /api/v1/payments/settle/{poId}` -> Expect `201 Created` with transaction reference and updated budget balance.

