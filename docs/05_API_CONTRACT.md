# 05_API_CONTRACT.md — ProcureShield AI

## Global Standard Conventions

### Base URL & Versioning
* **Production:** `https://api.procureshield.com/api/v1`
* **Staging:** `https://api.stage.procureshield.com/api/v1`
* **Local Development:** `http://localhost:8080/api/v1`

### Global Standard Error Envelope
All error responses across all eight modules return this uniform JSON structure via `@RestControllerAdvice`:

```json
{
  "timestamp": "2026-11-18T10:00:00Z",
  "status": 400,
  "error": "BAD_REQUEST",
  "message": "Validation failed: seatQuantity must be greater than or equal to 1",
  "path": "/api/v1/cart/items"
}
```

---

## 1. Authentication Service API (`auth`)

### 1.1 `POST /auth/register`
* **Description:** Registers a new enterprise employee or manager and maps them to a corporate department cost center.
* **Authentication:** Public (or restricted to `ROLE_PROCUREMENT_ADMIN` for enterprise pre-provisioning).
* **Path Variables & Query Parameters:** None.
* **Request Headers:** `Content-Type: application/json`
* **Validation Rules:**
  * `email`: Not blank, valid corporate email format, unique in database.
  * `password`: Minimum $8$ characters, at least $1$ uppercase, $1$ lowercase, $1$ digit, $1$ special character.
  * `fullName`: Not blank, max $100$ characters.
  * `departmentId`: Positive integer, must reference an existing `departments.id`.
  * `role`: Optional, defaults to `ROLE_EMPLOYEE`. Valid values: `ROLE_EMPLOYEE`, `ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN`, `ROLE_AUDITOR`.

**Request Body (`RegisterRequestDto`):**
```json
{
  "email": "ananya.rao@procureshield.com",
  "password": "SecurePassword@123",
  "fullName": "Ananya Rao",
  "departmentId": 1,
  "role": "ROLE_EMPLOYEE"
}
```

**Success Response (`201 Created`):**
```json
{
  "userId": 4,
  "email": "ananya.rao@procureshield.com",
  "fullName": "Ananya Rao",
  "role": "ROLE_EMPLOYEE",
  "departmentId": 1,
  "departmentName": "Platform Engineering",
  "isActive": true,
  "createdAt": "2026-11-18T10:05:00Z"
}
```

**Error Responses:**
* `400 Bad Request`: Validation failure on email format or password policy.
* `404 Not Found`: `departmentId` does not exist.
* `409 Conflict`: User with this email already registered.

---

### 1.2 `POST /auth/login`
* **Description:** Authenticates user credentials via BCrypt and issues a signed stateless JWT access token and refresh token.
* **Authentication:** Public (None).
* **Path Variables & Query Parameters:** None.
* **Validation Rules:** `email` and `password` must not be blank.

**Request Body (`LoginRequestDto`):**
```json
{
  "email": "abhishek@procureshield.com",
  "password": "SecurePassword@123"
}
```

**Success Response (`200 OK`):**
```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhYmhpc2hlayIsImF1dGgiOiJST0xFX1BST0NVUkVNRU5UX0FETUlOIiwiZGVwdElkIjoxLCJleHAiOjE3NjM1MTA0MDB9.s1F3...",
  "refreshToken": "d7a8f9e0-12b3-4c5d-8e9f-0123456789ab",
  "expiresInSeconds": 900,
  "user": {
    "id": 1,
    "email": "abhishek@procureshield.com",
    "fullName": "Abhishek Kumar",
    "role": "ROLE_PROCUREMENT_ADMIN",
    "departmentId": 1,
    "departmentName": "Platform Engineering"
  }
}
```

**Error Responses:**
* `400 Bad Request`: Missing email or password.
* `401 Unauthorized`: Bad credentials or deactivated user account.

---

### 1.3 `POST /auth/refresh`
* **Description:** Rotates an expired JWT access token using a valid, non-expired refresh token.
* **Authentication:** Public.
* **Path Variables & Query Parameters:** None.
* **Validation Rules:** `refreshToken` must not be blank.

**Request Body (`RefreshTokenRequestDto`):**
```json
{
  "refreshToken": "d7a8f9e0-12b3-4c5d-8e9f-0123456789ab"
}
```

**Success Response (`200 OK`):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "a1b2c3d4-56e7-89f0-1234-567890abcdef",
  "expiresInSeconds": 900
}
```

**Error Responses:**
* `401 Unauthorized`: Refresh token expired or revoked.

---

## 2. User & Department Cost-Center API (`user`)

### 2.1 `GET /users/me/budget`
* **Description:** Retrieves the authenticated user's profile and their assigned department's cost-center budget balance.
* **Authentication:** `Bearer <JWT>` (Any authenticated role).
* **Path Variables & Query Parameters:** None.
* **Validation Rules:** Valid active JWT token with an assigned department.

**Success Response (`200 OK`):**
```json
{
  "userId": 1,
  "fullName": "Abhishek Kumar",
  "email": "abhishek@procureshield.com",
  "role": "ROLE_PROCUREMENT_ADMIN",
  "department": {
  "id": 1,
  "name": "Platform Engineering",
  "costCenterCode": "CC-ENG-101",
  "annualBudget": 150000.00,
  "remainingBudget": 142500.00,
  "reservedBudget": 7500.00,
  "spentBudget": 0.00
  }
}
```

**Error Responses:**
* `401 Unauthorized`: Missing or invalid token.

---
### 2.2 `GET /departments`
* **Description:** Lists all enterprise departmental cost centers with remaining (uncommitted), reserved and spent budget balances.
* **Authentication:** `Bearer <JWT>` (`ROLE_PROCUREMENT_ADMIN`, `ROLE_AUDITOR`).
* **Path Variables & Query Parameters:** None.

**Success Response (`200 OK`):**
```json
[
  {
    "id": 1,
    "name": "Platform Engineering",
    "costCenterCode": "CC-ENG-101",
    "annualBudget": 150000.00,
    "remainingBudget": 142500.00,
    "reservedBudget": 7500.00,
    "spentBudget": 0.00
  },
  {
    "id": 2,
    "name": "Enterprise Security",
    "costCenterCode": "CC-SEC-202",
    "annualBudget": 90000.00,
    "remainingBudget": 90000.00,
    "reservedBudget": 0.00,
    "spentBudget": 0.00
  }
]
```

**Error Responses:**
* `401 Unauthorized`: Missing or invalid token.
* `403 Forbidden`: User lacks `ROLE_PROCUREMENT_ADMIN` or `ROLE_AUDITOR`.

---

### 2.3 `PATCH /departments/{id}/budget`
* **Description:** Updates a department's annual budget allocation. The change in `annualBudget` is applied to `remainingBudget` in the same transaction so the invariant `annualBudget = remainingBudget + reservedBudget + spentBudget` always holds. Reserved and spent amounts are never altered by this endpoint.
* **Authentication:** `Bearer <JWT>` (`ROLE_PROCUREMENT_ADMIN`).
* **Path Variables:** `id` (Long, required) — Department ID.
* **Query Parameters:** None.
* **Validation Rules:**
  * `annualBudget`: Required, numeric, $\ge 0$, max 2 decimal places.
  * Let `delta = newAnnualBudget - currentAnnualBudget`. The update is rejected if `currentRemainingBudget + delta < 0` (the new annual budget would be less than `reservedBudget + spentBudget`).
  * The service must load the department with `PESSIMISTIC_WRITE`, the same lock used for PO approval and settlement.

**Request Body (`DepartmentBudgetUpdateDto`):**
```json
{
  "annualBudget": 180000.00
}
```

**Success Response (`200 OK`):**
```json
{
  "id": 1,
  "name": "Platform Engineering",
  "costCenterCode": "CC-ENG-101",
  "annualBudget": 180000.00,
  "remainingBudget": 172500.00,
  "reservedBudget": 7500.00,
  "spentBudget": 0.00
}
```

**Error Responses:**
* `400 Bad Request`: Validation failure, or the new `annualBudget` is below `reservedBudget + spentBudget` (error code `INSUFFICIENT_BUDGET`).
* `403 Forbidden`: User lacks `ROLE_PROCUREMENT_ADMIN`.
* `404 Not Found`: Department ID does not exist.
---

## 3. Vendor Catalog & AI Risk Engine API (`product`)

### 3.1 `POST /products`
* **Description:** Creates a new SaaS tool or vendor contract listing in the catalog.
* **Authentication:** `Bearer <JWT>` (`ROLE_PROCUREMENT_ADMIN`).
* **Path Variables & Query Parameters:** None.
* **Validation Rules:**
  * `vendorName`, `productName`, `category`: Not blank.
  * `pricePerSeat`: Non-negative numeric value ($\ge 0$).
  * `renewalNoticeDays`: Non-negative integer.

**Request Body (`ProductCreateDto`):**
```json
{
  "vendorName": "Datadog Inc.",
  "productName": "Datadog Enterprise APM",
  "category": "Observability",
  "description": "Cloud monitoring and application performance observability platform.",
  "pricePerSeat": 300.00,
  "billingCycle": "ANNUAL",
  "renewalNoticeDays": 90
}
```

**Success Response (`201 Created`):**
```json
{
  "id": 10,
  "vendorName": "Datadog Inc.",
  "productName": "Datadog Enterprise APM",
  "category": "Observability",
  "pricePerSeat": 300.00,
  "billingCycle": "ANNUAL",
  "auditStatus": "PENDING_UPLOAD",
  "aiRiskScore": null,
  "aiRiskLevel": null,
  "isActive": true,
  "createdAt": "2026-11-18T10:10:00Z"
}
```

**Error Responses:**
* `400 Bad Request`: Validation failure on required fields or negative pricing.
* `403 Forbidden`: User lacks `ROLE_PROCUREMENT_ADMIN`.

---

### 3.2 `GET /products`
* **Description:** Lists catalog SaaS products with optional category filtering and maximum AI risk score.
* **Authentication:** `Bearer <JWT>` (Any role).
* **Path Variables:** None.
* **Query Parameters:**
  * `category` (string, optional)
  * `maxRiskScore` (integer $1\text{–}100$, optional)
  * `page` (integer, default `0`)
  * `size` (integer, default `10`)
* **Validation Rules:** `maxRiskScore` must be between $1$ and $100$ when supplied; `page >= 0` and `size >= 1`.

**Success Response (`200 OK`):**
```json
{
  "content": [
    {
      "id": 10,
      "vendorName": "Datadog Inc.",
      "productName": "Datadog Enterprise APM",
      "category": "Observability",
      "pricePerSeat": 300.00,
      "auditStatus": "READY",
      "aiRiskScore": 54,
      "aiRiskLevel": "HIGH",
      "renewalNoticeDays": 90
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

**Error Responses:**
* `400 Bad Request`: Invalid pagination or `maxRiskScore` outside $1\text{–}100$.
* `401 Unauthorized`: Missing or invalid token.

---

### 3.3 `POST /products/{id}/upload-sla`
* **Description:** Streams vendor SLA contract PDF to Cloudflare R2 and triggers background AI text extraction and scoring.
* **Authentication:** `Bearer <JWT>` (`ROLE_PROCUREMENT_ADMIN`).
* **Path Variables:** `id` (Long, required) — Target product ID.
* **Query Parameters:** None.
* **Headers:** `Content-Type: multipart/form-data`
* **Form Data:** `file` (Binary PDF, max $10\text{ MB}$).
* **Validation Rules:** Must be a non-empty valid PDF (`application/pdf`), size $\le 10\text{ MB}$.

**Success Response (`202 Accepted`):**
```json
{
  "productId": 10,
  "productName": "Datadog Enterprise APM",
  "slaPdfR2Key": "sla-contracts/2026/datadog-sla-v4.pdf",
  "auditStatus": "AUDITING",
  "message": "SLA PDF stored in Cloudflare R2. Background AI risk analysis started."
}
```

**Error Responses:**
* `400 Bad Request`: Non-PDF file uploaded or size exceeds $10\text{ MB}$.
* `403 Forbidden`: User lacks `ROLE_PROCUREMENT_ADMIN`.
* `404 Not Found`: Product ID does not exist.

---

### 3.4 `POST /products/{id}/retry-audit`
* **Description:** Re-queues the asynchronous AI SLA risk audit for a product whose prior LLM analysis failed or fell back to `MANUAL_REVIEW_REQUIRED` (per NFR-2).
* **Authentication:** `Bearer <JWT>` (`ROLE_PROCUREMENT_ADMIN`).
* **Path Variables:** `id` (Long, required) — Target product ID.
* **Query Parameters & Request Body:** None.
* **Validation Rules:** Product must exist, have a valid `slaPdfR2Key`, and have `auditStatus` in `('FAILED', 'MANUAL_REVIEW_REQUIRED')`.

**Success Response (`202 Accepted`):**
```json
{
  "productId": 10,
  "auditStatus": "AUDITING",
  "message": "AI SLA risk audit re-queued successfully."
}
```

**Error Responses:**
* `400 Bad Request`: Product has no uploaded SLA PDF or is not in `FAILED` / `MANUAL_REVIEW_REQUIRED` state.
* `403 Forbidden`: User lacks `ROLE_PROCUREMENT_ADMIN`.
* `404 Not Found`: Product ID does not exist.

---

### 3.5 `GET /products/{id}/risk-scorecard`
* **Description:** Returns the persisted structured JSONB AI risk scorecard for an audited SaaS product.
* **Authentication:** `Bearer <JWT>` (Any role).
* **Path Variables:** `id` (Long, required) — Target product ID.
* **Query Parameters:** None.

**Success Response (`200 OK`):**
```json
{
  "productId": 10,
  "vendorName": "Datadog Inc.",
  "productName": "Datadog Enterprise APM",
  "auditStatus": "READY",
  "aiRiskScore": 54,
  "aiRiskLevel": "HIGH",
  "scorecard": {
    "overallScore": 54,
    "riskLevel": "HIGH",
    "autoRenewalNoticeDays": 90,
    "dataResidencyCompliant": true,
    "uncappedLiabilityDetected": true,
    "flaggedClauses": [
      "Section 8.2: Automatic 24-month renewal unless written notice provided 90 days prior.",
      "Section 11.4: Vendor aggregate liability is capped at $500 regardless of breach severity."
    ],
    "auditorSummary": "Elevated vendor lock-in risk due to aggressive 90-day cancellation notice window."
  }
}
```

**Error Responses:**
* `401 Unauthorized`: Missing or invalid token.
* `404 Not Found`: Product ID does not exist.

---

## 4. Requisition Builder API (`cart`)

### 4.1 `POST /cart/items`
* **Description:** Adds SaaS license seats to the user's active requisition cart and calculates aggregated AI risk warnings.
* **Authentication:** `Bearer <JWT>` (`ROLE_EMPLOYEE`, `ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN`).
* **Path Variables & Query Parameters:** None.
* **Validation Rules:** `productId` must exist; `seatQuantity` must be $\ge 1$.

**Request Body (`CartItemRequestDto`):**
```json
{
  "productId": 10,
  "seatQuantity": 25
}
```

**Success Response (`200 OK`):**
```json
{
  "cartId": 5,
  "totalEstimatedCost": 7500.00,
  "highRiskWarning": true,
  "warningMessage": "Warning: 1 item in your requisition has an AI Risk Score below 60 (HIGH RISK).",
  "items": [
    {
      "itemId": 101,
      "productId": 10,
      "productName": "Datadog Enterprise APM",
      "seatQuantity": 25,
      "unitPrice": 300.00,
      "subtotal": 7500.00,
      "aiRiskScore": 54,
      "aiRiskLevel": "HIGH"
    }
  ]
}
```

**Error Responses:**
* `400 Bad Request`: `seatQuantity < 1` or missing `productId`.
* `404 Not Found`: Product ID does not exist.

---

### 4.2 `GET /cart`
* **Description:** Retrieves the authenticated user's active requisition cart items and total estimated cost.
* **Authentication:** `Bearer <JWT>` (`ROLE_EMPLOYEE`, `ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN`).
* **Path Variables & Query Parameters:** None.

**Success Response (`200 OK`):**
```json
{
  "cartId": 5,
  "itemCount": 1,
  "totalEstimatedCost": 7500.00,
  "highRiskWarning": true,
  "items": [
    {
      "itemId": 101,
      "productId": 10,
      "productName": "Datadog Enterprise APM",
      "seatQuantity": 25,
      "unitPrice": 300.00,
      "subtotal": 7500.00,
      "aiRiskScore": 54,
      "aiRiskLevel": "HIGH"
    }
  ]
}
```

---

### 4.3 `DELETE /cart/items/{itemId}`
* **Description:** Removes a line item from the active requisition cart.
* **Authentication:** `Bearer <JWT>` (`ROLE_EMPLOYEE`, `ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN`).
* **Path Variables:** `itemId` (Long, required) — Cart item ID.
* **Query Parameters:** None.
* **Success Response:** `204 No Content`.
* **Error Responses:** `404 Not Found` if cart item does not exist for the user.

---

## 5. Vendor Shortlist API (`wishlist`)

### 5.1 `POST /wishlist/{productId}`
* **Description:** Bookmarks a candidate SaaS vendor to the user's shortlist for multi-vendor evaluation.
* **Authentication:** `Bearer <JWT>` (`ROLE_EMPLOYEE`, `ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN`).
* **Path Variables:** `productId` (Long, required) — Target SaaS product ID.
* **Query Parameters:** None.
* **Validation Rules:** Product must exist and not already be bookmarked by the user.

**Request Body (`WishlistAddDto`):**
```json
{
  "notes": "Evaluate APM trace sampling pricing against Dynatrace before Q4 renewal."
}
```

**Success Response (`201 Created`):**
```json
{
  "id": 42,
  "userId": 1,
  "productId": 10,
  "productName": "Datadog Enterprise APM",
  "aiRiskScore": 54,
  "notes": "Evaluate APM trace sampling pricing against Dynatrace before Q4 renewal.",
  "createdAt": "2026-11-18T10:12:00Z"
}
```

**Error Responses:**
* `404 Not Found`: Product ID does not exist.
* `409 Conflict`: Product already bookmarked in user's shortlist.

---

### 5.2 `GET /wishlist`
* **Description:** Retrieves all bookmarked SaaS products for the authenticated user.
* **Authentication:** `Bearer <JWT>` (`ROLE_EMPLOYEE`, `ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN`).
* **Path Variables & Query Parameters:** None.

**Success Response (`200 OK`):**
```json
[
  {
    "id": 42,
    "productId": 10,
    "vendorName": "Datadog Inc.",
    "productName": "Datadog Enterprise APM",
    "pricePerSeat": 300.00,
    "aiRiskScore": 54,
    "notes": "Evaluate APM trace sampling pricing against Dynatrace before Q4 renewal."
  }
]
```

---

## 6. Purchase Order & PO State Machine API (`order`)

### 6.1 `POST /orders/checkout`
* **Description:** Converts the user's active cart into a formal Purchase Order in `PENDING_APPROVAL` status and clears the cart.
* **Authentication:** `Bearer <JWT>` (`ROLE_EMPLOYEE`, `ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN`).
* **Path Variables & Query Parameters:** None.
* **Validation Rules:** Cart must not be empty; `justification` non-blank; `contractEndDate` must be a future date.

**Request Body (`CheckoutRequestDto`):**
```json
{
  "justification": "Observability tooling for 2027 Kubernetes production migration.",
  "contractEndDate": "2027-11-18"
}
```

**Success Response (`201 Created`):**
```json
{
  "poId": 501,
  "poNumber": "PO-2026-0501",
  "status": "PENDING_APPROVAL",
  "totalAmount": 7500.00,
  "highestAiRiskScore": 54,
  "departmentId": 1,
  "requesterId": 1,
  "contractEndDate": "2027-11-18",
  "createdAt": "2026-11-18T10:15:00Z"
}
```

**Error Responses:**
* `400 Bad Request`: Requisition cart is empty or `contractEndDate` is in the past.

---

### 6.2 `GET /orders/{poId}`
* **Description:** Retrieves detailed Purchase Order information, line items, and immutable audit history.
* **Authentication:** `Bearer <JWT>` (Any role, scoped by department permissions).
* **Path Variables:** `poId` (Long, required) — Purchase Order ID.
* **Query Parameters:** None.

**Success Response (`200 OK`):**
```json
{
  "poId": 501,
  "poNumber": "PO-2026-0501",
  "status": "PENDING_APPROVAL",
  "totalAmount": 7500.00,
  "highestAiRiskScore": 54,
  "requesterName": "Abhishek Kumar",
  "departmentName": "Platform Engineering",
  "items": [
    {
      "productId": 10,
      "productName": "Datadog Enterprise APM",
      "seatQuantity": 25,
      "unitPrice": 300.00,
      "subtotal": 7500.00
    }
  ],
  "auditLogs": [
    {
      "actorName": "Abhishek Kumar",
      "previousStatus": null,
      "newStatus": "PENDING_APPROVAL",
      "comments": "Purchase Order created from checkout.",
      "timestamp": "2026-11-18T10:15:00Z"
    }
  ]
}
```

**Error Responses:**
* `403 Forbidden`: User cannot view orders outside their department scope.
* `404 Not Found`: Purchase Order ID does not exist.

---

### 6.3 `PATCH /orders/{poId}/status`

* **Description:** Advances the Purchase Order state machine (`DRAFT -> PENDING_APPROVAL`, `PENDING_APPROVAL -> APPROVED | REJECTED`, `APPROVED -> ISSUED`, `ISSUED -> COMPLETED`) and appends an immutable entry to `po_audit_logs`.
* **Authentication:** `Bearer <JWT>` (`ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN`).
* **Path Variables:** `poId` (Long, required) — Target Purchase Order ID.
* **Query Parameters:** None.
* **Validation Rules:**
  * `newStatus`: Must be a valid target state (`PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `ISSUED`, or `COMPLETED`).
  * PENDING_APPROVAL -> APPROVED: Allowed for ROLE_DEPARTMENT_HEAD (own department only) or ROLE_PROCUREMENT_ADMIN. In one transaction the service locks the department row (PESSIMISTIC_WRITE), requires department.remainingBudget >= order.totalAmount, then decrements remainingBudget and increments reservedBudget by totalAmount, sets approver_id, and appends the audit log..
  * PENDING_APPROVAL -> REJECTED: Allowed for ROLE_DEPARTMENT_HEAD or ROLE_PROCUREMENT_ADMIN; comments mandatory. No budget effect (nothing is reserved before approval).
  * `APPROVED -> ISSUED`: Strictly restricted to `ROLE_PROCUREMENT_ADMIN` when dispatching the formal PO to the external SaaS vendor.

**Request Body (`OrderStatusUpdateDto`) — Example 1 (Internal Approval):**
```json
{
  "newStatus": "APPROVED",
  "comments": "Approved under Platform Engineering Q4 budget allocation."
}
```

**Request Body (`OrderStatusUpdateDto`) — Example 2 (Commercial Vendor Issuance):**
```json
{
  "newStatus": "ISSUED",
  "comments": "PO dispatched to Datadog enterprise sales team."
}
```

**Success Response (`200 OK`):**
```json
{
  "poId": 501,
  "poNumber": "PO-2026-0501",
  "previousStatus": "APPROVED",
  "currentStatus": "ISSUED",
  "reviewedBy": "Abhishek Kumar (Procurement Admin)",
  "auditLogId": 9002,
  "updatedAt": "2026-11-18T10:45:00Z"
}
```

**Error Responses:**
* `400 Bad Request`: Insufficient departmental budget, missing rejection comments, or invalid state machine transition.
* `403 Forbidden`: User lacks required role (e.g., non-admin attempting `APPROVED -> ISSUED`) or order belongs to another department.
* `404 Not Found`: Purchase Order ID does not exist.

---

## 7. Budget Settlement API (`payment`)

### 7.1 `POST /payments/settle/{poId}`
* **Description:** Settles an ISSUED Purchase Order: in one transaction, locks the department row, moves totalAmount from reservedBudget to spentBudget, marks the PO COMPLETED, appends an audit log, and generates a receipt.
Validation: PO must be in ISSUED state and not previously settled. Budget is not re-checked, since it was reserved at approval.
* **Authentication:** `Bearer <JWT>` (`ROLE_PROCUREMENT_ADMIN`).
* **Path Variables:** `poId` (Long, required) — Target Purchase Order ID.
* **Query Parameters & Request Body:** None.
* **Validation Rules:** Purchase Order must be in `ISSUED` (or `APPROVED`) state, not previously settled, and covered by `department.remainingBudget`.

**Success Response (`201 Created`):**
```json
{
  "paymentId": 301,
  "poNumber": "PO-2026-0501",
  "transactionRef": "TXN-PS-8849201",
  "amountPaid": 7500.00,
  "paymentMethod": "BUDGET_LEDGER",
  "departmentBudget": { "remaining": 142500.00, "reserved": 0.00, "spent": 7500.00 },  "poStatus": "COMPLETED",
  "paidAt": "2026-11-18T11:00:00Z"
}
```

**Error Responses:**
* `400 Bad Request`: PO is not in ISSUED status. Delete the "budget is insufficient" clause.
* `403 Forbidden`: User lacks `ROLE_PROCUREMENT_ADMIN`.
* `404 Not Found`: Purchase Order ID does not exist.
* `409 Conflict`: Purchase Order has already been settled.

---

## 8. Compliance & PO Alerts API (`notification`)

### 8.1 `GET /notifications`
* **Description:** Retrieves real-time PO approval updates and $60/30/15$-day SLA auto-renewal warnings for the logged-in user.
* **Authentication:** `Bearer <JWT>` (Any role).
* **Path Variables:** None.
* **Query Parameters:** `unreadOnly` (boolean, optional, default `true`).

**Success Response (`200 OK`):**
```json
[
  {
    "notificationId": 701,
    "title": "Purchase Order Approved: PO-2026-0501",
    "message": "Your requisition for Datadog Enterprise APM (25 seats) has been approved.",
    "notificationType": "PO_STATUS_CHANGE",
    "isRead": false,
    "createdAt": "2026-11-18T10:30:00Z"
  },
  {
    "notificationId": 702,
    "title": "SLA Auto-Renewal Notice Window (90 Days)",
    "message": "Datadog Enterprise APM contract renewal notice window closes in 90 days.",
    "notificationType": "SLA_RENEWAL_ALERT",
    "isRead": false,
    "createdAt": "2026-11-18T10:31:00Z"
  }
]
```

---

### 8.2 `PATCH /notifications/{id}/read`
* **Description:** Marks a single notification as read.
* **Authentication:** `Bearer <JWT>` (Any role).
* **Path Variables:** `id` (Long, required) — Notification ID.
* **Query Parameters & Request Body:** None.
* **Success Response:** `204 No Content`.
* **Error Responses:** `404 Not Found` if notification ID does not belong to the authenticated user.