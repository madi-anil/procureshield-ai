# 01_PROJECT_CONTEXT.md — ProcureShield AI

## 1. Vision & Executive Summary
**ProcureShield AI (`procureshield-ai`)** is an enterprise B2B SaaS Procurement and AI Vendor Risk Management portal built under the TrainingMug ADF v1.0 framework. It eliminates shadow IT and manual contract bottlenecks by guiding corporate software purchasing through an 8-service e-commerce workflow (from Requisition Cart to Purchase Order), while embedding an asynchronous AI Compliance Auditor (Gemini/Claude API) that parses vendor SLA PDFs stored in Cloudflare R2 and persists structured JSONB risk scorecards ($1\text{–}100$) in PostgreSQL.

## 2. Core Problem Statement
1. **Shadow IT & Unvetted SaaS Spend:** Employees purchase unapproved software trials across fragmented spreadsheets and email threads, leading to duplicate subscriptions and compliance exposure.
2. **Hidden SLA Traps & Synchronous Review Bottlenecks:** Procurement teams manually read 30-page vendor SLA PDFs, frequently missing hidden auto-renewal windows (e.g., 90-day lock-in notices) and uncapped liability clauses.

## 3. Technology Matrix

| Subsystem / Layer | Technology Stack | Storage / Integration | Primary Function |
| :--- | :--- | :--- | :--- |
| **Frontend Web App** | React 18 / Vite / TypeScript / Tailwind | Browser Storage (JWT) | 9-screen SPA, Vendor Catalog, Requisition Cart, AI Risk Badges, PO Dashboard. |
| **Core Backend API** | Java 21 / Spring Boot 3.x | PostgreSQL 16 *(Flyway)* | 8 modular services (`auth`, `user`, `product`, `cart`, `wishlist`, `order`, `payment`, `notification`). |
| **Security & Auth** | Spring Security 6 / JJWT | Stateless JWT + Refresh | BCrypt password hashing, JWT rotation, 4-role RBAC enforcement. |
| **Object Storage** | AWS S3 SDK v2 (Java) | Cloudflare R2 Bucket | Zero-egress storage for vendor logos, SOC2/ISO certs, and SLA contract PDFs (up to 10 MB). |
| **AI Risk Engine** | Spring AI / REST Client + Apache PDFBox | Gemini / Claude API | Asynchronous PDF text extraction, LLM fact extraction, and deterministic rubric scoring into a JSONB scorecard ($1\text{–}100$). |
| **API Docs & Testing** | SpringDoc OpenAPI 3 / JUnit 5 / Mockito | Swagger UI / Postman | Interactive REST documentation and $\ge 80\%$ service-layer unit test coverage. |
| **DevOps & CI/CD** | Docker / Docker Compose / GitHub Actions | Vercel + Azure Container Apps | Multi-stage container builds, automated test execution, and cloud deployment pipelines. |

## 4. Architecture & 8-Service Build Sequence (ADF Phase 9)
The backend follows a strict **Layered Architecture** (`Controller -> Service -> Repository -> Entity/DTO`) and will be implemented one module at a time in the mandatory ADF sequence:

1. **Authentication Service (`auth`):** Stateless JWT login, registration, refresh token rotation, and 4-role RBAC (`ROLE_EMPLOYEE`, `ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN`, `ROLE_AUDITOR`).
2. **User Service (`user`):** Employee profiles, role assignment, and departmental cost-center budget ledgers (remaining, reserved and spent).
3. **Product Service (`product`):** Approved Vendor & SaaS License Catalog; handles Cloudflare R2 uploads (SLA PDFs, logos, SOC2 certs) and triggers the asynchronous AI Compliance Auditor ($1\text{–}100$ JSONB Risk Scorecard).
4. **Cart Service (`cart`):** Procurement Requisition builder where employees select software license seats and review aggregated AI contract risk warnings prior to submission.
5. **Wishlist Service (`wishlist`):** Vendor Shortlist & Comparison board for bookmarking candidate SaaS tools during evaluation.
6. **Order Service (`order`):** Purchase Order (PO) lifecycle state machine (`PENDING_APPROVAL -> APPROVED | REJECTED`, `APPROVED -> ISSUED`; `COMPLETED` is reached only through settlement) with immutable compliance audit logs.
7. **Payment Service (`payment`):** Simulated corporate invoice settlement, reserved-to-spent departmental budget conversion, and payment receipt generation.
8. **Notification Service (`notification`):** Automated email and in-app alerts for PO status transitions, high-risk SLA flags, and $60/30/15$-day contract auto-renewal reminders.

## 5. Master Folder Structure

```text
procureshield-ai/
├── docs/                               # ADF v1.0 Documentation Suite
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
│   └── 11_PROMPTS.md                   # Engineering Prompts
├── backend/                            # Java 21 / Spring Boot 3.x Modular Monolith
│   ├── src/main/java/com/procureshield/
│   │   ├── config/                     # Security, CORS, R2 S3Client, Async, Swagger
│   │   ├── controller/                 # REST Controllers (8 Modules)
│   │   ├── dto/                        # Request & Response DTOs with @Valid
│   │   ├── entity/                     # JPA Entities & Enums
│   │   ├── exception/                  # @RestControllerAdvice & Custom Exceptions
│   │   ├── repository/                 # Spring Data JPA Repositories
│   │   ├── security/                   # JwtAuthenticationFilter & TokenProvider
│   │   └── service/                    # Business Logic, AI Auditor, R2 Storage
│   ├── src/main/resources/
│   │   ├── db/migration/               # Flyway SQL Scripts (V1__init_schema.sql)
│   │   └── application.yml
│   └── src/test/java/com/procureshield/# JUnit 5 & Mockito Service Unit Tests
├── frontend/                           # React 18 + Vite + TypeScript SPA
├── docker/                             # Dockerfiles & docker-compose.yml
├── postman/                            # Exported Postman API Collections
├── .github/workflows/                  # GitHub Actions CI/CD (deploy.yml)
└── README.md
```

## 6. Coding Standards (ADF Section 8)

| Category | Rules & Enforcement |
| :--- | :--- |
| **Mandatory Practices** | SOLID Principles, Layered Architecture, Request/Response DTO Pattern, Spring Data Repository Pattern, Constructor Injection (`@RequiredArgsConstructor`), Jakarta Bean Validation (`@Valid`), Global Exception Handler (`@RestControllerAdvice`), Swagger (`@Operation`) Documentation, REST Naming Standards, JUnit 5 + Mockito Testing, and Structured SLF4J Logging. |
| **Prohibited Anti-Patterns** | Business logic inside Controllers, exposing JPA Entities directly in API responses, hardcoded credentials/URLs, duplicate code, long methods, magic numbers, or modifying unrelated modules during feature implementation. |

## 7. Git Branching & Commit Strategy
- **`main`**: Production-ready code deployed to `procureshield.com` and `api.procureshield.com`.
- **`stage`**: Pre-production staging branch deployed to `stage.procureshield.com` and `api.stage.procureshield.com`.
- **`develop`**: Primary integration branch for completed features (`dev.procureshield.com` and `api.dev.procureshield.com`).
- **`feature/<module>`**: Isolated branches following the 8-service build order (`feature/auth`, `feature/user`, `feature/products`, `feature/cart`, `feature/wishlist`, `feature/orders`, `feature/payment`, `feature/notifications`).
- **Commit Convention**: Conventional commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`) with daily GitHub pushes and Pull Request reviews prior to merging.

## 8. Deployment & Cloud Routing Strategy
- **Frontend Hosting**: Static React/Vite bundle deployed on **Cloudflare Pages** or **Vercel**.
- **Backend Hosting**: Dockerized Spring Boot 3.x container deployed on **Azure Container Apps** or **Render**.
- **Database & Object Storage**: Managed **PostgreSQL 16** (with Flyway migrations and `jsonb` columns) and **Cloudflare R2** (`procureshield-assets` bucket).
- **CI/CD & Secrets**: Automated via `.github/workflows/deploy.yml` using GitHub Repository Secrets (the authoritative list is `10_DEPLOYMENT.md` §5).