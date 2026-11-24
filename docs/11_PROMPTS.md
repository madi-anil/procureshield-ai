# SYSTEM CONTEXT & DEVELOPER MASTER PROMPT: PROCURESHIELD AI

> **Instructions for the AI Coding Agent (Claude Code / Cursor / Codex / Antigravity / Freebuff):**
> Read this document completely before generating any code or documentation updates. This document defines the exact project context, modular monolith architecture, tech stack, directory structure, scope boundaries, and step-by-step workflow for **ProcureShield AI (`procureshield-ai`)**.
> You must strictly follow all engineering rules and scope boundaries below. Do not add out-of-scope features, alter the stack, or modify unrelated modules.

## 1. PROJECT OVERVIEW & GOALS

- **Project Title:** ProcureShield AI (`procureshield-ai`) – AI Enterprise Vendor & Procurement Risk Portal
- **Developer Name:** Madikonda Anil
- **Methodology:** TrainingMug AI Development Framework (ADF v1.0)
- **Target Delivery Window:** 2 Weeks (14 Days)
- **Core Problem Solved:** Eliminates shadow IT, unvetted SaaS spend, and hidden SLA auto-renewal traps by guiding corporate software requests through an 8-service procurement flow while asynchronously parsing vendor SLA PDFs (stored in Cloudflare R2) with an LLM to persist structured `JSONB` risk scorecards (1–100) in PostgreSQL 16.

## 2. SCOPE BOUNDARIES & CONSTRAINTS

### In-Scope (v1.0 MVP - Must Build)
1. **8-Service Backend (`auth`, `user`, `product`, `cart`, `wishlist`, `order`, `payment`, `notification`):** Java 21 / Spring Boot 3.x modular monolith with Flyway migrations, JWT + single-use refresh token rotation, 4-role RBAC, and pessimistic budget locking.
2. **Cloudflare R2 & Async AI Risk Auditor (`product`):** Streams SLA PDFs (`<= 10 MB`), logos, and certificates to Cloudflare R2 via AWS S3 SDK v2, extracts text with Apache PDFBox, calls Gemini/Claude API asynchronously (`202 Accepted`), and writes a `1–100` `JSONB` scorecard with `MANUAL_REVIEW_REQUIRED` fallback and retry support.
3. **Requisition, Shortlist & PO State Machine (`cart`, `wishlist`, `order`):** Seat-based requisition cart with risk warnings (`score < 60`) and budget checks, vendor shortlist comparison, and PO state machine (`DRAFT -> PENDING_APPROVAL -> APPROVED/REJECTED -> ISSUED -> COMPLETED`) with immutable `po_audit_logs`.
4. **Budget Settlement & Alerts (`payment`, `notification`):** Transactional cost-center budget deduction (`TXN-PS-*` receipt) and in-app/event alerts including `60/30/15`-day contract renewal reminders.
5. **Frontend (React 18 + Vite + TypeScript + Tailwind):** 9-screen SPA with role-aware navigation, TanStack Query, Axios refresh interceptor, and 4 UI states (Loading, Error, Empty, Validation) on every screen.
6. **Infrastructure & CI/CD:** `docker-compose.yml` for local PostgreSQL 16, multi-stage `Dockerfile`s, and `.github/workflows/deploy.yml` with JaCoCo `>= 80%` service coverage check.

### Strictly Out-of-Scope (Do NOT Build in v1.0)
- **Endpoint Scanning Agents:** Local desktop/network agents scanning employee machines for installed software or malware.
- **Cloudflare Workers for JVM Backend:** Never target Cloudflare Workers for Spring Boot JAR execution; host backend containers on Azure Container Apps or Render and static SPA on Cloudflare Pages or Vercel.
- **Live ERP & Real Payment Gateways:** Live SAP/Oracle NetSuite sync and real bank/card transfers.

## 3. TECHNOLOGY MATRIX & MODULE BOUNDARIES

| Module / Layer | Primary Tech Stack | Storage / Integration | Key Responsibilities |
| :--- | :--- | :--- | :--- |
| **frontend** | React 18 / Vite / TypeScript / Tailwind / TanStack Query | Browser Auth State + Axios Interceptor | 9 screens, role-aware `AppShell`, risk badges, 4 UI states, SLA upload & polling. |
| **auth & user** | Java 21 / Spring Boot 3.x / Spring Security 6 / JJWT | PostgreSQL 16 (`users`, `refresh_tokens`, `departments`) | BCrypt (cost 12), JWT + refresh rotation, 4-role RBAC, cost-center budgets. |
| **product** | Java 21 / Spring Boot 3.x / PDFBox / S3 SDK v2 | Cloudflare R2 + Gemini/Claude API + PostgreSQL (`JSONB`) | Catalog CRUD, R2 PDF upload (`202 Accepted`), async risk scoring, retry endpoint. |
| **cart & wishlist** | Java 21 / Spring Boot 3.x / Spring Data JPA | PostgreSQL 16 (`cart_items`, `wishlist_items`) | Seat requisitions, `< 60` risk warnings, budget pre-check, vendor comparison list. |
| **order, payment & notification** | Java 21 / Spring Boot 3.x / `@Async` / `@Scheduled` | PostgreSQL 16 (`purchase_orders`, `po_audit_logs`, `payments`, `notifications`) | PO state transitions, append-only audit logs, pessimistic lock settlement, alerts. |

## 4. SYSTEM DATA FLOW & ARCHITECTURE PIPELINE

```text
+-------------------------------------------------------------------+
|                   React 18+ Frontend (Vite / TS)                  |
|                 Hosted on Cloudflare Pages / Vercel               |
+---------------------------------+---------------------------------+
                                  | HTTPS (Bearer JWT)
                                  v
+-------------------------------------------------------------------+
|            Cloudflare DNS & Reverse Proxy (api.domain)            |
+---------------------------------+---------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|          Spring Boot 3.x Backend (Azure Container Apps / Render)  |
|  +-------------------------------------------------------------+  |
|  | Security Filter Chain: JWT Auth, Refresh Rotation & RBAC    |  |
|  +-------------------------------------------------------------+  |
|  | Controllers: Auth | User | Product | Cart | Wishlist |      |  |
|  |              Order | Payment | Notification                 |  |
|  +-------------------------------------------------------------+  |
|  | Services: Layered Business Logic, PO State Machine, @Async  |  |
|  +-------------------------------------------------------------+  |
|  | Repositories: Spring Data JPA / Pessimistic Locks / Flyway  |  |
|  +--------------+-------------------------------+--------------+  |
+-----------------|-------------------------------|-----------------+
                  |                               |
                  v                               v
+-----------------------------------+   +---------------------------+
|     Managed PostgreSQL 16 DB      |   |   Cloudflare R2 Storage   |
|  (Relational Tables + JSONB Risk) |   | (SLA PDFs, Certs, Logos)  |
+-----------------------------------+   +-------------+-------------+
                                                      |
                                        Spring Boot extracts text
                                        via PDFBox & calls LLM API
                                                      v
                                        +---------------------------+
                                        |  Gemini / Claude LLM API  |
                                        |  (Structured Risk JSON)   |
                                        +---------------------------+

```

## 5. REPOSITORY DIRECTORY LAYOUT (ADF v1.0)

```text
procureshield-ai/
├── docs/
│   ├── 01_PROJECT_CONTEXT.md
│   ├── 02_REQUIREMENTS.md
│   ├── 03_ARCHITECTURE.md
│   ├── 04_DATABASE.md
│   ├── 05_API_CONTRACT.md
│   ├── 06_UI_FLOW.md
│   ├── 07_TASKS.md
│   ├── 08_CODING_STANDARDS.md
│   ├── 09_TESTING.md
│   ├── 10_DEPLOYMENT.md
│   └── 11_PROMPTS.md
├── backend/
│   ├── src/main/java/com/procureshield/
│   │   ├── config/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── exception/
│   │   ├── repository/
│   │   ├── security/
│   │   └── service/
│   ├── src/main/resources/
│   │   ├── db/migration/V1__init_procureshield_schema.sql
│   │   └── application.yml
│   ├── src/test/
│   └── Dockerfile
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── services/
│   │   ├── context/
│   │   └── types/
│   └── Dockerfile
├── docker/
│   └── docker-compose.yml
├── postman/
│   └── ProcureShield_AI.postman_collection.json
├── .github/workflows/
│   └── deploy.yml
└── README.md

```

## 6. DATABASE & CONTRACT HIGHLIGHTS

### Core PostgreSQL Tables (`V1__init_procureshield_schema.sql`)

* `departments`, `users`, `refresh_tokens`, `products` (with `ai_risk_score` and `JSONB` `ai_risk_scorecard`), `cart_items`, `wishlist_items`, `purchase_orders`, `purchase_order_items`, `po_audit_logs` (with `DO INSTEAD NOTHING` update/delete rules), `payments`, and `notifications`.

### Key API & State Rules

* `POST /api/v1/auth/login` -> `200 OK` (`accessToken`, `refreshToken`, `user`); `POST /api/v1/auth/refresh` -> rotates refresh token.
* `POST /api/v1/products/{id}/upload-sla` -> `202 Accepted` (`AUDITING`); `GET /api/v1/products/{id}/risk-scorecard` -> `200 OK`; `POST /api/v1/products/{id}/retry-audit` -> `202 Accepted` (`ROLE_PROCUREMENT_ADMIN`).
* `POST /api/v1/orders/checkout` -> `201 Created` (`PENDING_APPROVAL`); `PATCH /api/v1/orders/{poId}/status` -> `200 OK` (`APPROVED`, `REJECTED`, `ISSUED` with `ROLE_PROCUREMENT_ADMIN` guard for `APPROVED -> ISSUED`); `POST /api/v1/payments/settle/{poId}` -> `201 Created` (`COMPLETED`, budget deducted).

## 7. MANDATORY CODING STANDARDS & DAILY PROMPT TEMPLATES

1. **SOLID & Layered Architecture:** `Controller -> Service (Interface + Impl) -> Repository -> Entity / DTO` with `@RequiredArgsConstructor` constructor injection; no business logic in controllers and no exposed JPA entities in APIs.
2. **Error Handling, Validation & Logging:** `@RestControllerAdvice` returning uniform error JSON, `@Valid` on DTOs, `SLF4J` (`@Slf4j`) logging without secrets, and `spring.jpa.hibernate.ddl-auto: validate`.
3. **Single-Module Isolation & Verification:** Modify only files in the assigned `feature/<module>` scope, state assumptions before coding, and keep JUnit 5 + Mockito service line coverage `>= 80%`.

### Daily Implementation Prompt (Coding Agent)

```text
Context: ProcureShield AI (ADF v1.0). Read docs/01_PROJECT_CONTEXT.md, docs/04_DATABASE.md, docs/05_API_CONTRACT.md, and docs/08_CODING_STANDARDS.md.
Task: Implement [MODULE_NAME] ([TASK_ID]).
Constraints:
1. Follow Controller -> Service -> Repository -> Entity/DTO with @RequiredArgsConstructor and @Valid DTOs.
2. Modify ONLY files required for [MODULE_NAME]; do not touch unrelated modules.
3. State assumptions first, provide code and JUnit 5 + Mockito unit tests, then stop.

```

### Independent Adversarial Audit Prompt (Reviewer Agent)

```text
Audit the diff for [MODULE_NAME] against docs/05_API_CONTRACT.md and docs/08_CODING_STANDARDS.md.
Check for: (1) HTTP status/field/RBAC mismatches, (2) business logic in controllers or exposed entities, (3) missing @Transactional / PESSIMISTIC_WRITE locks or unguarded async/LLM failures, and (4) missing boundary/error tests.

```

## 8. STEP-BY-STEP EXECUTION ROADMAP

* [ ] **Step 1:** Set up repository layout and commit `docs/01` through `docs/11` + `README.md`.
* [ ] **Step 2:** Configure `docker/docker-compose.yml` (PostgreSQL 16) and `V1__init_procureshield_schema.sql`.
* [ ] **Step 3:** Scaffold Java 21 / Spring Boot 3.x project, dependencies, and `@RestControllerAdvice`.
* [ ] **Step 4:** Implement `auth` (`feature/auth`) with JWT + single-use refresh rotation and RBAC.
* [ ] **Step 5:** Implement `user` (`feature/user`) for profiles and departmental cost-center budgets.
* [ ] **Step 6:** Implement `product` (`feature/products`) with Cloudflare R2 upload (`202`), async PDFBox + LLM scoring, fallback, and retry.
* [ ] **Step 7:** Implement `cart` (`feature/cart`) and `wishlist` (`feature/wishlist`) with risk & budget warnings.
* [ ] **Step 8:** Implement `order` (`feature/orders`), `payment` (`feature/payment`), and `notification` (`feature/notifications`) with immutable audit logs, pessimistic locking, and renewal alerts.
* [ ] **Step 9:** Build React 18 SPA (`frontend`) across all 9 screens with 4 UI states per screen.
* [ ] **Step 10:** Achieve `>= 80%` service unit test coverage (JaCoCo), export Postman collection, and deploy via Docker + GitHub Actions