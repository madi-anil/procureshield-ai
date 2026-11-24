# 03_ARCHITECTURE.md

## 1. High-Level System Topology 

The high-level topology illustrates how the React 18 SPA communicates through Cloudflare DNS to the Spring Boot 3.x backend, which orchestrates relational persistence in PostgreSQL 16, binary PDF/logo storage in Cloudflare R2, and asynchronous SLA contract auditing via the Gemini/Claude LLM API.

```text
+-----------------------------------------------------------------------------------+
|                         React 18+ Frontend (Vite / TS / Tailwind)                 |
|                           Hosted on Cloudflare Pages / Vercel                     |
+-----------------------------------------+-----------------------------------------+
                                          | HTTPS (Authorization: Bearer <JWT>)
                                          v
+-----------------------------------------------------------------------------------+
|                   Cloudflare DNS & Reverse Proxy (api.procureshield.com)          |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                 Spring Boot 3.x Backend (Java 21 on Azure Container Apps)         |
|  +-----------------------------------------------------------------------------+  |
|  | Security Filter Chain: Stateless JWT Authenticator & 4-Role RBAC Guard      |  |
|  +-----------------------------------------------------------------------------+  |
|  | REST Controllers (8 ADF Modules):                                           |  |
|  | Auth | User | Product | Cart | Wishlist | Order | Payment | Notification    |  |
|  +-----------------------------------------------------------------------------+  |
|  | Service Layer (@Transactional & @Async):                                    |  |
|  | Layered Business Logic | PO State Machine | Apache PDFBox + AI Risk Engine  |  |
|  +-----------------------------------------------------------------------------+  |
|  | Persistence & Integration Clients:                                          |  |
|  | Spring Data JPA Repositories | AWS S3 SDK v2 (R2) | Spring AI / REST Client |  |
|  +--------------+-------------------------------+------------------------------+  |
+-----------------|-------------------------------|------------------------------|--+
                  |                               |                              |
                  v (JDBC / Flyway)               v (S3 API)                     v (HTTPS REST)
+--------------------------+     +----------------------------+     +---------------------------+
|  Managed PostgreSQL 16   |     |   Cloudflare R2 Storage    |     |  Gemini / Claude LLM API  |
| (Entities + JSONB Score) |     | (SLA PDFs, Certs, Logos)   |     | (Structured Risk JSON)    |
+--------------------------+     +----------------------------+     +---------------------------+
```

---

## 2. Modular Service & Component Diagram

Following **ADF Principle 4 (Build One Module at a Time)** and **Section 8 (Coding Standards)**, every module enforces a strict unidirectional layered architecture without exposing JPA entities to the presentation tier.

```text
+-----------------------------------------------------------------------------------+
|                               Incoming HTTP Request                               |
|                        (JSON Payload / Multipart PDF Upload)                      |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| 1. Controller Layer (`com.procureshield.controller`)                              |
|    - Validates Request DTOs via Jakarta `@Valid`                                  |
|    - Enforces method-level RBAC via `@PreAuthorize("hasRole('...')")`             |
|    - Documented with SpringDoc OpenAPI `@Operation` & `@ApiResponse` annotations  |
+-----------------------------------------+-----------------------------------------+
                                          | Request DTO
                                          v
+-----------------------------------------------------------------------------------+
| 2. Service Layer (`com.procureshield.service`)                                    |
|    - Constructor-injected dependencies (`@RequiredArgsConstructor`)               |
|    - Executes domain rules, budget checks, and PO state transitions               |
|    - Maps between DTOs and JPA Entities                                           |
|    - Orchestrates `@Async` AI SLA auditing & Cloudflare R2 object uploads         |
+--------------------+-----------------------------------------+--------------------+
                     | JPA Entity                              | Integration Calls
                     v                                         v
+-----------------------------------------+   +-------------------------------------+
| 3. Repository Layer (`.repository`)     |   | 4. External Integration Adapters    |
|    - Spring Data JPA Interfaces         |   |    - `R2StorageClient` (AWS S3 v2)  |
|    - Custom `@Query` & JSONB queries    |   |    - `PdfBoxTextExtractor`          |
|    - Append-only `PoAuditLogRepository` |   |    - `AiComplianceClient` (LLM API) |
+--------------------+--------------------+   +-------------------------------------+
                     |
                     v
+-----------------------------------------+
| 5. Entity & Database Layer (`.entity`)  |
|    - JPA `@Entity` classes & Enums      |
|    - Flyway Migration (`V1__init.sql`)  |
+-----------------------------------------+
```

---

## 3. Service Responsibilities (8-Module ADF Mapping)

| # | ADF Service Module | Package / Scope | Core Architectural Responsibilities | Backing Tables & External Integrations |
| :- | :--- | :--- | :--- | :--- |
| **1** | **Authentication (`auth`)** | `security`, `service.auth` | BCrypt password verification (cost $12$), stateless JWT access token generation, refresh token rotation, and RBAC claim extraction. | `users`, `refresh_tokens` |
| **2** | **User & Cost Centers (`user`)** | `service.user` | Employee profile management, vendor organization registry, and departmental cost-center annual/remaining budget ledgers. | `users`, `departments`, `vendor_orgs` |
| **3** | **Product & AI Auditor (`product`)** | `service.product`, `service.ai` | SaaS catalog CRUD, streaming logos/certs/SLA PDFs ($\le 10\text{ MB}$) to Cloudflare R2, PDFBox text extraction, and async LLM risk scoring ($1\text{–}100$). | `products`, `sla_risk_reports` (`jsonb`), Cloudflare R2, Gemini/Claude API |
| **4** | **Requisition Builder (`cart`)** | `service.cart` | Active employee procurement cart, seat quantity calculation, departmental budget pre-check, and aggregated AI risk warning banners (score $< 60$). | `carts`, `cart_items` |
| **5** | **Vendor Shortlist (`wishlist`)** | `service.wishlist` | Bookmarking candidate SaaS products into an evaluation board for side-by-side compliance and pricing comparison. | `wishlists`, `wishlist_items` |
| **6** | **Purchase Orders (`order`)** | `service.order` | Converts requisitions into formal POs, enforces budget escalation thresholds, runs the PO state machine, and writes immutable audit logs. | `purchase_orders`, `po_items`, `po_audit_logs` |
| **7** | **Corporate Settlement (`payment`)** | `service.payment` | Settles `ISSUED` Purchase Orders against departmental cost-center budgets or simulated Stripe/Razorpay webhooks and generates payment receipts. | `payments`, `departments` |
| **8** | **Compliance Alerts (`notification`)** | `service.notification` | Dispatches in-app and email alerts on PO state transitions, high-risk SLA detections, and $60/30/15$-day contract auto-renewal windows. | `notifications`, Spring Mail / Webhook |

---

## 4. Authentication & 4-Role RBAC Flow

ProcureShield AI enforces stateless JWT security across four enterprise roles (`ROLE_EMPLOYEE`, `ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN`, `ROLE_AUDITOR`).

```text
Client (React SPA)           AuthController / Filter          AuthService / JwtProvider          PostgreSQL 16
        |                               |                                 |                            |
        |--- 1. POST /api/v1/auth/login |                                 |                            |
        |    {email, password} -------->|                                 |                            |
        |                               |--- 2. authenticate(dto) ------->|                            |
        |                               |                                 |--- 3. findByEmail(email) ->|
        |                               |                                 |<-- 4. User + BCrypt Hash --|
        |                               |                                 |                            |
        |                               |                                 |--- 5. Verify BCrypt (12)   |
        |                               |                                 |--- 6. Sign Access JWT      |
        |                               |                                 |    (sub, role, deptId, exp)|
        |                               |                                 |--- 7. Save Refresh Token ->|
        |<-- 8. 200 OK -----------------|                                 |                            |
        |    {accessToken, refreshToken}|                                 |                            |
        |                               |                                 |                            |
        |--- 9. GET /api/v1/orders      |                                 |                            |
        |    Bearer <accessToken> ----->|                                 |                            |
        |                               |--- 10. Validate Signature & Exp |                            |
        |                               |--- 11. Set SecurityContext      |                            |
        |                               |    & Check @PreAuthorize        |                            |
        |<-- 12. 200 OK (Filtered POs) -|                                 |                            |
```

---

## 5. Asynchronous AI Audit Pipeline & Purchase Order State Machine

### 5.1 Asynchronous SLA PDF Compliance Pipeline (`< 500 ms` Non-Blocking Response)

To satisfy **NFR-1**, uploading a vendor SLA PDF never blocks the HTTP request thread waiting for LLM inference:

```text
Procurement Admin      ProductController       Cloudflare R2       AiComplianceService (@Async)     Gemini/Claude API     PostgreSQL 16
        |                      |                     |                          |                           |                   |
        |-- 1. Upload SLA PDF->|                     |                          |                           |                   |
        |   (Multipart <=10MB) |-- 2. PutObject ---->|                          |                           |                   |
        |                      |<- 3. R2 Object Key -|                          |                           |                   |
        |                      |                     |                          |                           |                   |
        |                      |-- 4. Save Product (audit_status = AUDITING) ---|---------------------------|------------------>|
        |                      |-- 5. Trigger Async Audit (productId, pdfBytes)>|                           |                   |
        |<- 6. 202 Accepted ---|                     |                          |                           |                   |
        |   (status: AUDITING) |                     |                          |-- 7. PDFBox Extract Text  |                   |
        |                      |                     |                          |-- 8. Prompt LLM (JSON) -->|                   |
        |                      |                     |                          |<- 9. Risk Scorecard ------|                   |
        |                      |                     |                          |   (score: 1-100, clauses) |                   |
        |                      |                     |                          |-- 10. Persist JSONB & Set status = READY ---->|
        |                      |                     |                          |   (Fallback: MANUAL_REVIEW_REQUIRED on error) |
```

### 5.2 Purchase Order Lifecycle State Machine

Every state transition in `OrderService` executes inside an ACID `@Transactional` boundary and appends an immutable record to `po_audit_logs`:

```text
                       +-----------------------+
                       |         DRAFT         |
                       +-----------+-----------+
                                   |
                                   | Employee submits Requisition Cart
                                   v
                       +-----------------------+
                       |   PENDING_APPROVAL    |
                       +-----+-----------+-----+
                             |           |
      Reject (Dept Head /    |           | Approve (Dept Head if <= dept limit,
      Procurement Admin)     |           | or Procurement Admin if escalated)
                             v           v
                  +----------+--+     +--+--------------------+
                  |  REJECTED   |     |       APPROVED        |
                  +-------------+     +-----------+-----------+
                                                  |
                                                  | Procurement Admin issues PO to Vendor
                                                  v
                                      +-----------+-----------+
                                      |        ISSUED         |
                                      +-----------+-----------+
                                                  |
                                                  | PaymentService settles Invoice
                                                  | & deducts Departmental Budget
                                                  v
                                      +-----------+-----------+
                                      |       COMPLETED       |
                                      +-----------------------+
```

---

## 6. Multi-Environment Cloud Deployment Diagram

Following **ADF Phases 12–14**, code merges flow from `feature/*` branches through `develop`, `stage`, and `main`, triggering automated GitHub Actions builds (`.github/workflows/deploy.yml`) mapped to environment-specific subdomains.

```text
+---------------------------------------------------------------------------------------------------+
|                                  GitHub Repository (`procureshield-ai`)                           |
|          feature/*  ──────►  develop (Dev)  ──────►  stage (Staging)  ──────►  main (Prod)        |
+-------------------------------------------------+-------------------------------------------------+
                                                  |
                                                  | Triggers `.github/workflows/deploy.yml`
                                                  | (1. Checkout -> 2. Maven JUnit Test -> 3. Docker Build & Push)
                                                  v
+---------------------------------------------------------------------------------------------------+
|                                   Environment Subdomain Routing                                   |
|                                                                                                   |
|  +-----------------------------+  +-----------------------------+  +---------------------------+  |
|  |     Development (`dev`)     |  |      Staging (`stage`)      |  |    Production (`main`)    |  |
|  | FE: dev.procureshield.com   |  | FE: stage.procureshield.com |  | FE: procureshield.com     |  |
|  | BE: api.dev.procureshield...|  | BE: api.stage.procureshi... |  | BE: api.procureshield.com |  |
|  +-----------------------------+  +-----------------------------+  +---------------------------+  |
+------------------------+---------------------------------------------------------+----------------+
                         |                                                         |
                         v (Static SPA Bundles)                                    v (Docker Containers)
+-------------------------------------------------+       +-----------------------------------------+
|        Cloudflare Pages / Vercel Hosting        |       |     Azure Container Apps / Render       |
|      (React 18 + Vite + TypeScript Build)       |       |   (Spring Boot 3.x + Java 21 Runtime)   |
+-------------------------------------------------+       +--------------------+--------------------+
                                                                               |
                                                  +----------------------------+----------------------------+
                                                  |                                                         |
                                                  v                                                         v
                                 +---------------------------------+                       +---------------------------------+
                                 |   Managed PostgreSQL 16 Cloud   |                       |     Cloudflare R2 Object Store  |
                                 |  (SSL Connection via `DB_URL`)  |                       |   (`procureshield-assets` S3)   |
                                 +---------------------------------+                       +---------------------------------+
```