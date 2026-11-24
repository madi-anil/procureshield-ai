# 08. CODING STANDARDS & ENGINEERING RULES — ProcureShield AI

## 1. Core Architectural Rules

* **SOLID Principles:** Enforce Single Responsibility, Open-Closed, Liskov Substitution, Interface Segregation, and Dependency Inversion across all Java 21 backend services and React 18 TypeScript modules
* **Strict Layered Architecture:** Every backend module (`auth`, `user`, `product`, `cart`, `wishlist`, `order`, `payment`, `notification`) must follow the unidirectional flow: `Controller` -> `Service (Interface + Impl)` -> `Repository` -> `Entity / DTO`.
* **No Business Logic in Controllers:** REST controllers must strictly handle HTTP request mapping, Jakarta `@Valid` input validation, `@PreAuthorize` role checks, Swagger `@Operation` documentation, and delegating to service interfaces.
* **No Exposed JPA Entities:** Database `@Entity` classes must never be accepted as request bodies or returned directly in REST API responses[cite: 4, 6, 17]. Map all inputs and outputs using dedicated Request/Response DTOs (or Java 21 `record` classes).
* **Constructor Injection Only:** Field injection (`@Autowired` on fields) is strictly prohibited[cite: 4, 6, 17]. Use Lombok's `@RequiredArgsConstructor` with `private final` dependencies.

---

## 2. Global Exception Handling (`@RestControllerAdvice`)

All Spring Boot REST controllers must rely on a centralized `@RestControllerAdvice` handler that returns the standardized JSON error envelope defined in `05_API_CONTRACT.md` (`timestamp`, `status`, `error`, `message`, `path`):

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponseDto error = new ErrorResponseDto(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String validationMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ErrorResponseDto error = new ErrorResponseDto(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                "Validation failed: " + validationMessage,
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InsufficientBudgetException.class)
    public ResponseEntity<ErrorResponseDto> handleBudgetOverrun(
            InsufficientBudgetException ex, HttpServletRequest request) {
        log.warn("Budget overrun blocked at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponseDto error = new ErrorResponseDto(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "INSUFFICIENT_BUDGET",
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnhandledException(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponseDto error = new ErrorResponseDto(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred while processing the request.",
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```

## 3. Concurrency, Database & Async Integration Standards

* **Pessimistic Locking on Budget Ledgers:** PO approval (reserve) and invoice settlement (reserved → spent) must run in `@Transactional` and load the department via `@Lock(LockModeType.PESSIMISTIC_WRITE)`. Every mutation must preserve `annual = remaining + reserved + spent`. A check-only read of `remaining_budget` without mutation is prohibited.
* **Append-Only Audit Trails:** `PoAuditLogRepository` must never expose `delete` or `update` operations; all state transitions must insert a new immutable record into `po_audit_logs`.
* **Non-Blocking AI SLA Audits (`@Async`):** Multipart PDF uploads to Cloudflare R2 must validate MIME type (`application/pdf`) and file size ($\le 10\text{ MB}$) before streaming to S3Client, return `202 Accepted` immediately, and delegate Apache PDFBox text extraction + Gemini API scoring to a dedicated `TaskExecutor` thread pool.
* **LLM Resilience & Deterministic Output:** Gemini API calls must enforce structured JSON output (`temperature = 0.0`). Any network timeout, HTTP `429`, or malformed JSON response must be caught gracefully and update `audit_status` to `MANUAL_REVIEW_REQUIRED` without crashing the worker thread.
* **Flyway Schema Governance:** `spring.jpa.hibernate.ddl-auto` must be set to `validate`. All schema changes must be authored as sequential SQL migrations under `src/main/resources/db/migration/` (`V1__init_procureshield_schema.sql`).

---

## 4. Frontend TypeScript & React 18 Standards (`/frontend`)

* **Strict TypeScript:** `strict: true` must be enabled in `tsconfig.json`. The `any` type is strictly prohibited—define explicit interfaces matching backend DTOs in `src/types/`.
* **Server State & Auth Handling:** Use **TanStack Query (React Query)** for all REST API data fetching, caching, and invalidation, and attach `Authorization: Bearer <token>` via a centralized Axios/Fetch interceptor with automatic `/auth/refresh` token rotation on `401` responses.
* **Mandatory 4-State UI Matrix:** Every data-driven screen and component must explicitly render distinct **Loading** (skeleton loaders), **Error** (alert banner + retry button), **Empty** (zero-state CTA), and **Data** states.
* **Security & XSS Prevention:** Never render raw HTML (`dangerouslySetInnerHTML` is prohibited) when displaying AI-extracted SLA clauses or user justification notes.

---

## 5. Logging, Validation & Prohibited Anti-Patterns

* **Structured SLF4J Logging:** Use `@Slf4j` in Java services and exception handlers (`log.info`, `log.warn`, `log.error`). `System.out.println` and `e.printStackTrace()` are strictly prohibited. Never log raw JWT tokens, passwords, or R2/Gemini API keys.
* **Jakarta Bean Validation:** Annotate all Request DTO fields with `jakarta.validation.constraints` (`@NotBlank`, `@NotNull`, `@Email`, `@Positive`, `@Min`, `@Future`) and enforce `@Valid` on every `@RequestBody` controller parameter.
* **Prohibited Anti-Patterns:**
* Hardcoded credentials, bucket names, or environment URLs (must be injected via `${ENV_VAR}` in `application.yml`).
* Modifying unrelated module files while working on an isolated `feature/<module>` branch.
* Committing code with failing tests or JUnit 5 + Mockito service line coverage below `80%`.

