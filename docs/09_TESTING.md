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
                .requester(User.builder().id(9L).build())   // approver (id 2) must differ from requester
                .requiresEscalation(false)
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

## 3. Concurrency Integration Test (Testcontainers)

Locking cannot be verified with mocks, so one `@SpringBootTest` + Testcontainers (PostgreSQL 16) test is mandatory (not `@Transactional` at class level):

* **Scenario:** 10 threads concurrently approve 10 different `PENDING_APPROVAL` POs of 3,000.00 each against a department with 10,000.00 remaining.
* **Pass criteria:** exactly 3 approvals succeed, 7 fail with `InsufficientBudgetException`, no other exception occurs, `remaining = 1000.00`, `reserved = 9000.00`, and `annual = remaining + reserved + spent`.
* **Proof the test works:** it must fail when `PESSIMISTIC_WRITE` is removed from the department query.

## 4. AI Risk Auditor Test Matrix

* **Rubric (`RiskScoringService`, table-driven):** one case per penalty band in `03_ARCHITECTURE.md` §5.1.1, plus the worked example (90-day notice, 24-month term, `LOW` cap → 54 / `HIGH`), the floor at 1, and the level boundaries (80 → `LOW`, 79 → `MEDIUM`, 60 → `MEDIUM`, 59 → `HIGH`).
* **Failure paths (`AiComplianceService`, mocked LLM client):** timeout and `429` → `MANUAL_REVIEW_REQUIRED` / `LLM_UNAVAILABLE`; malformed JSON or extra keys → `INVALID_LLM_OUTPUT`; encrypted PDF → `FAILED` / `UNREADABLE_PDF`; scanned PDF (< 500 chars) → `FAILED` / `NO_EXTRACTABLE_TEXT`.
* **Prompt injection:** contract text containing "ignore previous instructions" never reaches the LLM client (verify with `verify(llm, never())`) and ends in `SUSPECTED_PROMPT_INJECTION`; an LLM response that tries to include an `overallScore` key is rejected.
* **Recovery:** a product in `AUDITING` with `audit_started_at` older than the timeout is moved to `MANUAL_REVIEW_REQUIRED` / `AUDIT_TIMEOUT` by `StuckAuditReaper`.
* **Audit immutability (Testcontainers):** `UPDATE`, `DELETE` and `TRUNCATE` on `po_audit_logs` each raise an exception.

## 5. Postman Collection Verification Checklist

* `POST /api/v1/auth/login` -> Expect `200 OK` with JWT `accessToken` and `refreshToken`.
* `POST /api/v1/products/{id}/upload-sla` -> Expect `202 Accepted` with `auditStatus: "AUDITING"`.
* `GET /api/v1/products/{id}/risk-scorecard` -> Expect `200 OK` with `overallScore` and `riskLevel`.
* `POST /api/v1/cart/items` -> Expect `200 OK` with seat subtotal and `highRiskWarning` flag.
* `POST /api/v1/cart/items` for a product with `auditStatus: "AUDITING"` -> Expect `409 Conflict` (`PRODUCT_NOT_AUDITED`).
* `POST /api/v1/orders/checkout` -> Expect `201 Created` with `poNumber` and `PENDING_APPROVAL` status.
* `PATCH /api/v1/orders/{poId}/status` -> Expect `200 OK` with updated status and audit log entry.
* `POST /api/v1/payments/settle/{poId}` -> Expect `201 Created` with transaction reference and updated budget balance.
* `POST /api/v1/auth/register` with `"role": "ROLE_PROCUREMENT_ADMIN"` in the body -> Expect `201 Created` with `role: "ROLE_EMPLOYEE"`.
* `PATCH /api/v1/users/{id}/role` as `ROLE_EMPLOYEE` -> Expect `403 Forbidden`; as admin on own ID -> Expect `403 Forbidden`.
* `PATCH /api/v1/orders/{poId}/status` with `"newStatus": "COMPLETED"` -> Expect `400 Bad Request`.
* `POST /api/v1/payments/settle/{poId}` on an `APPROVED` (not `ISSUED`) PO -> Expect `400 Bad Request`; settling twice -> Expect `409 Conflict`.
* `PATCH /api/v1/orders/{poId}/status` `APPROVED` by the PO's own requester -> Expect `403 Forbidden`.
* `PATCH /api/v1/orders/{poId}/status` `APPROVED` by a Dept Head on a PO above the threshold -> Expect `403 Forbidden`; by a Procurement Admin -> Expect `200 OK`.
* `POST /api/v1/orders/checkout` after a carted product's SLA was re-uploaded (status `AUDITING`) -> Expect `409 Conflict`.
* `GET /api/v1/orders` as `ROLE_EMPLOYEE` -> Expect `200 OK` containing only the caller's own POs.