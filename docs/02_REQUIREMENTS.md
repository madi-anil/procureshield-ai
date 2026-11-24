# 02. REQUIREMENTS ENGINEERING

## 1. User Roles (4-Tier RBAC)
* **`ROLE_EMPLOYEE`:** Browses the approved SaaS catalog, bookmarks tools to the shortlist, builds procurement requisitions (Cart), and submits purchase requests.
* **`ROLE_DEPARTMENT_HEAD`:** Reviews, approves, or rejects purchase requisitions within their departmental cost-center budget limit.
* **`ROLE_PROCUREMENT_ADMIN`:** Manages vendor catalog CRUD, uploads SLA PDFs to Cloudflare R2, triggers AI risk audits, approves high-value POs, and settles corporate invoices.
* **`ROLE_AUDITOR`:** Read-only governance access to inspect AI SLA risk scorecards, vendor certificates, and immutable Purchase Order approval logs.

## 2. Functional Requirements (FR)

### Module 1 & 2: Authentication (`auth`) & User Cost Centers (`user`)
* **FR-1.1:** System shall authenticate users via email/password with BCrypt (cost 12) and issue short-lived JWT access tokens + refresh tokens.
* **FR-1.2:** System shall maintain departmental cost centers with annual budget allocations, "remaining (uncommitted), reserved and spent balances", and user-to-department mappings.

### Module 3: Vendor Catalog, Cloudflare R2 & AI Risk Auditor (`product`)
* **FR-2.1:** System shall allow `ROLE_PROCUREMENT_ADMIN` to perform CRUD operations on vendor profiles and SaaS license tiers.
* **FR-2.2:** System shall upload vendor logos, SOC2/ISO certificates, and SLA contract PDFs (up to 10 MB) to Cloudflare R2.
* **FR-2.3:** System shall asynchronously extract text from uploaded SLA PDFs and invoke the Gemini/Claude API to produce a structured `JSONB` scorecard (`overallScore` 1–100, `autoRenewalDays`, `flaggedClauses`, `riskLevel`).

### Module 4 & 5: Requisition Builder (`cart`) & Vendor Shortlist (`wishlist`)
* **FR-3.1:** System shall allow employees to add SaaS license seats to a Requisition Cart and display aggregated AI risk warnings if any item has a risk score $< 60$.
* **FR-3.2:** System shall allow users to bookmark candidate SaaS tools into a Wishlist shortlist for side-by-side comparison.

### Module 6, 7 & 8: Purchase Orders (`order`), Settlement (`payment`) & Alerts (`notification`)
* **FR-4.1:** System shall manage Purchase Orders across the state machine: `DRAFT` $\rightarrow$ `PENDING_APPROVAL` $\rightarrow$ `APPROVED` / `REJECTED` $\rightarrow$ `ISSUED` $\rightarrow$ `COMPLETED`.
* **FR-4.2:** System shall write an immutable audit trail record (`po_audit_logs`) on every PO state transition.
* **FR-4.3:** System shall settle ISSUED POs by converting the reserved amount into spent budget on the department ledger and dispatching status/renewal alerts (60/30/15 days).

## 3. Non-Functional Requirements (NFR)
* **NFR-1 (Async Performance):** SLA PDF upload endpoint shall return `202 Accepted` in $< 500\text{ms}$ while executing PDF parsing and LLM scoring on a background thread pool.
* **NFR-2 (Resilience):** If the Gemini/Claude API times out, the product's `audit_status` shall fallback to `MANUAL_REVIEW_REQUIRED` without failing catalog creation.
* **NFR-3 (Data Integrity):** All PostgreSQL schema migrations shall be managed via Flyway (`V1__init_procureshield_schema.sql`), and audit logs shall be append-only.

## 4. User Stories & Acceptance Criteria

### User Story US-01: Asynchronous AI SLA Contract Auditing
> **As a** Procurement Admin,  
> **I want to** upload a vendor's SLA PDF when adding a SaaS tool to the catalog,  
> **So that** the AI engine automatically scores legal/security risks (1–100) and flags hidden auto-renewal traps.

* **Acceptance Criteria:**
  1. Valid PDF ($\le 10\text{ MB}$) is uploaded to Cloudflare R2 and `audit_status` is set to `AUDITING`.
  2. Background worker extracts text, prompts the LLM, and saves the structured JSONB scorecard in PostgreSQL.
  3. Status updates to `READY` and displays a color-coded badge (`LOW`, `MEDIUM`, `HIGH` risk) on the catalog card.

### User Story US-02: Requisition Checkout to Purchase Order Approval
> **As an** Employee,  
> **I want to** convert my SaaS requisition cart into a Purchase Order,  
> **So that** my Department Head can approve the seat licenses against our cost-center budget.

* **Acceptance Criteria:**
  1. Submitting the cart creates a Purchase Order in `PENDING_APPROVAL` state and clears the active cart.
  2. Department Head approves the PO; the system atomically verifies total_amount <= department.remaining_budget and reserves that amount.
  3. An immutable entry is recorded in `po_audit_logs` and a notification is triggered.

## 5. Out of Scope (Phase 1)
* Local desktop endpoint scanning agents for binary malware/software discovery.
* Live two-way ERP integrations (SAP, Oracle NetSuite) and real bank wire transfers.