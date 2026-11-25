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
* **Description:** Self-registers a new enterprise employee and maps them to a corporate department cost center. Always creates the account as `ROLE_EMPLOYEE`; privileged roles can only be granted later by an admin (`PATCH /users/{id}/role`, §2.4).
* **Authentication:** Public (None).
* **Path Variables & Query Parameters:** None.
* **Request Headers:** `Content-Type: application/json`
* **Validation Rules:**
  * `email`: Not blank, valid corporate email format, unique in database.
  * `password`: Minimum $8$ characters, at least $1$ uppercase, $1$ lowercase, $1$ digit, $1$ special character.
  * `fullName`: Not blank, max $100$ characters.
  * `departmentId`: Positive integer, must reference an existing `departments.id`.
    * `role`: **Not accepted.** `RegisterRequestDto` has no role field. Any `role` value sent by the client is ignored and the account is created as `ROLE_EMPLOYEE`.

**Request Body (`RegisterRequestDto`):**
```json
{
  "email": "ananya.rao@procureshield.com",
  "password": "SecurePassword@123",
  "fullName": "Ananya Rao",
  "departmentId": 1,
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
---

### 2.4 `PATCH /users/{id}/role`
  * **Description:** Assigns a role to an existing user. This is the only way to create `ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN` or `ROLE_AUDITOR` accounts. The target user's refresh tokens are revoked so the new role takes effect at next login (an already-issued access token stays valid for at most `expiresInSeconds`, 900 s).
  * **Authentication:** `Bearer <JWT>` (`ROLE_PROCUREMENT_ADMIN`).
  * **Path Variables:** `id` (Long, required) — Target user ID.
  * **Query Parameters:** None.
  * **Validation Rules:**
  * `role`: Required. One of `ROLE_EMPLOYEE`, `ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN`, `ROLE_AUDITOR`.
  * The caller cannot change their own role. This also guarantees the last admin can never be demoted.
  * Each change is logged at INFO with the actor ID, target ID, old role and new role.

**Request Body (`UserRoleUpdateDto`):**
```json
{
  "role": "ROLE_DEPARTMENT_HEAD"
}
```

**Success Response (`200 OK`):**
```json
{
  "id": 4,
  "email": "ananya.rao@procureshield.com",
  "fullName": "Ananya Rao",
  "role": "ROLE_DEPARTMENT_HEAD",
  "departmentId": 1
}
```

**Error Responses:**
  * `400 Bad Request`: Missing or invalid `role`.
  * `403 Forbidden`: Caller lacks `ROLE_PROCUREMENT_ADMIN`, or is changing their own role.
  * `404 Not Found`: User ID does not exist.

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
  * **Description:** Streams vendor SLA contract PDF to Cloudflare R2 and triggers background AI text extraction and scoring. Sets `auditStatus` to `AUDITING` and clears any previous score and scorecard, so the product is not purchasable until the new audit is `READY`.
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
  * **Description:** Returns the persisted structured JSONB AI risk scorecard for an audited SaaS product. `overallScore` and `riskLevel` are computed by the backend rubric (`rubricVersion`); `auditFailureReason` is set when the status is `FAILED` or `MANUAL_REVIEW_REQUIRED`.
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
  "auditFailureReason": null,
  "aiRiskScore": 54,
  "aiRiskLevel": "HIGH",
  "scorecard": {
    "overallScore": 54,
    "riskLevel": "HIGH",
    "autoRenewalNoticeDays": 90,
    "dataResidencyCompliant": true,
    "uncappedLiabilityDetected": false,
    "autoRenewalTermMonths": 24,
    "vendorLiabilityCap": "LOW",
    "rubricVersion": "1.0",
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

### 3.6 `GET /products/{id}`
  * **Description:** Returns one catalog product with its audit status and risk summary. Inactive products are visible only to `ROLE_PROCUREMENT_ADMIN`.
  * **Authentication:** `Bearer <JWT>` (Any role).
  * **Path Variables:** `id` (Long, required) — Product ID.
  * **Query Parameters:** None.

**Success Response (`200 OK`):**
```json
{
  "id": 10,
  "vendorName": "Datadog Inc.",
  "productName": "Datadog Enterprise APM",
  "category": "Observability",
  "description": "Cloud monitoring and application performance observability platform.",
  "pricePerSeat": 300.00,
  "billingCycle": "ANNUAL",
  "hasLogo": true,
  "hasSoc2Cert": true,
  "hasSlaPdf": true,
  "auditStatus": "READY",
  "aiRiskScore": 54,
  "aiRiskLevel": "HIGH",
  "renewalNoticeDays": 90,
  "isActive": true,
  "createdAt": "2026-11-18T10:10:00Z"
}
```

**Error Responses:**
  * `401 Unauthorized`: Missing or invalid token.
  * `404 Not Found`: Product does not exist (or is inactive and the caller is not an admin).

---

### 3.7 `PUT /products/{id}`
  * **Description:** Updates a product's commercial fields. Changing `pricePerSeat` never alters existing Purchase Orders (line items snapshot `unit_price`).
  * **Authentication:** `Bearer <JWT>` (`ROLE_PROCUREMENT_ADMIN`).
  * **Path Variables:** `id` (Long, required) — Product ID.
  * **Validation Rules:** Same as `POST /products` (§3.1).

**Request Body (`ProductUpdateDto`):** same fields as `ProductCreateDto`.

**Success Response (`200 OK`):** Same body as §3.6.

**Error Responses:**
  * `400 Bad Request`: Validation failure.
  * `403 Forbidden`: User lacks `ROLE_PROCUREMENT_ADMIN`.
  * `404 Not Found`: Product ID does not exist.

---

### 3.8 `DELETE /products/{id}`
  * **Description:** Soft-deletes a product (`is_active = false`). It disappears from the catalog; any cart containing it reports `checkoutBlocked: true`. Historical Purchase Orders are unaffected.
  * **Authentication:** `Bearer <JWT>` (`ROLE_PROCUREMENT_ADMIN`).
  * **Path Variables:** `id` (Long, required) — Product ID.
  * **Success Response:** `204 No Content`.
  * **Error Responses:** `403 Forbidden` (not admin); `404 Not Found` (unknown product).

---

### 3.9 `POST /products/{id}/upload-asset`
  * **Description:** Uploads a vendor logo or SOC2/ISO certificate to Cloudflare R2 (FR-2.2). Does not trigger an AI audit.
  * **Authentication:** `Bearer <JWT>` (`ROLE_PROCUREMENT_ADMIN`).
  * **Path Variables:** `id` (Long, required) — Product ID.
  * **Query Parameters:** `type` (required): `LOGO` or `SOC2_CERT`.
  * **Headers:** `Content-Type: multipart/form-data`
  * **Form Data:** `file` (binary).
  * **Validation Rules:** `LOGO`: `image/png` or `image/jpeg`, max $2\text{ MB}$. `SOC2_CERT`: `application/pdf`, max $10\text{ MB}$.

**Success Response (`200 OK`):**
```json
{
  "productId": 10,
  "type": "SOC2_CERT",
  "r2Key": "certs/2026/datadog-soc2.pdf"
}
```

**Error Responses:**
  * `400 Bad Request`: Missing/invalid `type`, wrong MIME type, or file too large.
  * `403 Forbidden`: User lacks `ROLE_PROCUREMENT_ADMIN`.
  * `404 Not Found`: Product ID does not exist.

---

## 4. Requisition Builder API (`cart`)

### 4.1 `POST /cart/items`
  * **Description:** Adds SaaS license seats to the user's active requisition cart and calculates aggregated AI risk warnings. Only products whose SLA audit is `READY` can be added.
  * **Authentication:** `Bearer <JWT>` (`ROLE_EMPLOYEE`, `ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN`).
  * **Path Variables & Query Parameters:** None.
  * **Validation Rules:** `productId` must exist and be active; the product must have `auditStatus = READY` with a non-null `aiRiskScore`; `seatQuantity` must be $\ge 1$.

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
  * `409 Conflict`: Product's SLA audit is not `READY` (error code `PRODUCT_NOT_AUDITED`).

---

### 4.2 `GET /cart`
  * **Description:** Retrieves the authenticated user's active requisition cart items and total estimated cost. `checkoutBlocked` is `true` when any item's product is no longer `READY` or active (for example, its SLA was re-uploaded after the item was added). `highRiskWarning` is computed only from items that have a score.
  * **Authentication:** `Bearer <JWT>` (`ROLE_EMPLOYEE`, `ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN`).
  * **Path Variables & Query Parameters:** None.

**Success Response (`200 OK`):**
```json
{
  "itemCount": 1,
  "checkoutBlocked": false,
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
      "aiRiskLevel": "HIGH",
      "auditStatus": "READY"
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

### 5.3 `DELETE /wishlist/{id}`
  * **Description:** Removes a bookmarked product from the authenticated user's shortlist.
  * **Authentication:** `Bearer <JWT>` (`ROLE_EMPLOYEE`, `ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN`).
  * **Path Variables:** `id` (Long, required) — Wishlist item ID (not the product ID).
  * **Success Response:** `204 No Content`.
  * **Error Responses:** `404 Not Found` if the item does not exist for the user.

---

## 6. Purchase Order & PO State Machine API (`order`)

### 6.1 `POST /orders/checkout`
  * **Description:** Converts the user's active cart into a formal Purchase Order in `PENDING_APPROVAL` status and clears the cart. The order is flagged `requiresEscalation: true` when `totalAmount` exceeds the configured `approval.escalation-threshold` (default 10000.00).
  * **Authentication:** `Bearer <JWT>` (`ROLE_EMPLOYEE`, `ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN`).
  * **Path Variables & Query Parameters:** None.
  * **Validation Rules:** Cart must not be empty; every cart item's product must still be active with `auditStatus = READY`; `justification` non-blank; `contractEndDate` must be a future date. `highestAiRiskScore` is computed from current product scores at checkout, not from values cached in the cart.

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
  * `409 Conflict`: One or more cart items are not purchasable (`PRODUCT_NOT_AUDITED`); the message names the affected products.

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
  "requiresEscalation": false,
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
  * **Description:** Advances the Purchase Order state machine (`PENDING_APPROVAL -> APPROVED | REJECTED`, `APPROVED -> ISSUED`) and appends an immutable entry to `po_audit_logs`.
  * **Authentication:** `Bearer <JWT>` (`ROLE_DEPARTMENT_HEAD`, `ROLE_PROCUREMENT_ADMIN`).
  * **Path Variables:** `poId` (Long, required) — Target Purchase Order ID.
  * **Query Parameters:** None.
  * **Validation Rules:**
  * `newStatus`: `APPROVED`, `REJECTED` or `ISSUED` only. `COMPLETED` is set only by `POST /payments/settle/{poId}`; sending it here returns `400`.
  * PENDING_APPROVAL -> APPROVED: Allowed for ROLE_DEPARTMENT_HEAD (own department only) or ROLE_PROCUREMENT_ADMIN. In one transaction the service locks the department row (PESSIMISTIC_WRITE), requires department.remainingBudget >= order.totalAmount, then decrements remainingBudget and increments reservedBudget by totalAmount, sets approver_id, and appends the audit log.
  * PENDING_APPROVAL -> REJECTED: Allowed for ROLE_DEPARTMENT_HEAD or ROLE_PROCUREMENT_ADMIN; comments mandatory. No budget effect (nothing is reserved before approval).
  * `APPROVED -> ISSUED`: Strictly restricted to `ROLE_PROCUREMENT_ADMIN` when dispatching the formal PO to the external SaaS vendor.
  * **Separation of duties:** The requester of a PO can never move it to `APPROVED`, `ISSUED` or `COMPLETED` (`403`, error code `SEPARATION_OF_DUTIES`). The requester may move it to `REJECTED` to withdraw it (`comments` mandatory).
  * **Escalation:** If `requiresEscalation` is `true`, only `ROLE_PROCUREMENT_ADMIN` may approve; a `ROLE_DEPARTMENT_HEAD` receives `403` (error code `ESCALATION_REQUIRED`).
  * `approver_id` is set only when the PO becomes `APPROVED`.

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
* `403 Forbidden`: User lacks required role (e.g., non-admin attempting `APPROVED -> ISSUED`), order belongs to another department, user is the PO requester (`SEPARATION_OF_DUTIES`), or the PO requires admin approval (`ESCALATION_REQUIRED`).
* `404 Not Found`: Purchase Order ID does not exist.

---

### 6.4 `GET /orders`
* **Description:** Lists Purchase Orders, newest first. Visibility is scoped by role: `ROLE_EMPLOYEE` sees only POs they requested; `ROLE_DEPARTMENT_HEAD` sees their department's POs; `ROLE_PROCUREMENT_ADMIN` and `ROLE_AUDITOR` see all.
* **Authentication:** `Bearer <JWT>` (Any role).
* **Path Variables:** None.
* **Query Parameters:**
  * `status` (enum, optional): `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `ISSUED`, `COMPLETED`.
  * `departmentId` (Long, optional): honoured only for `ROLE_PROCUREMENT_ADMIN` and `ROLE_AUDITOR`.
  * `page` (integer, default `0`), `size` (integer, default `10`).
* **Validation Rules:** `page >= 0`, `size >= 1`; unknown `status` returns `400`.

**Success Response (`200 OK`):**
```json
{
  "content": [
    {
      "poId": 501,
      "poNumber": "PO-2026-0501",
      "status": "PENDING_APPROVAL",
      "totalAmount": 7500.00,
      "highestAiRiskScore": 54,
      "requiresEscalation": false,
      "requesterName": "Abhishek Kumar",
      "departmentName": "Platform Engineering",
      "createdAt": "2026-11-18T10:15:00Z"
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

**Error Responses:**
* `400 Bad Request`: Invalid pagination or `status`.
* `401 Unauthorized`: Missing or invalid token.

---

## 7. Budget Settlement API (`payment`)

### 7.1 `POST /payments/settle/{poId}`
* **Description:** Settles an `ISSUED` Purchase Order. In one transaction it locks the department row, moves `totalAmount` from `reservedBudget` to `spentBudget`, marks the PO `COMPLETED`, appends an audit log, and generates a receipt. Budget is not re-checked, because it was reserved at approval.
* **Authentication:** `Bearer <JWT>` (`ROLE_PROCUREMENT_ADMIN`).
* **Path Variables:** `poId` (Long, required) — Target Purchase Order ID.
* **Query Parameters & Request Body:** None.
* **Validation Rules:** Purchase Order must be in `ISSUED` state and not previously settled. The settling user must not be the PO requester (`403`, `SEPARATION_OF_DUTIES`).

**Success Response (`201 Created`):**
```json
{
  "paymentId": 301,
  "poNumber": "PO-2026-0501",
  "transactionRef": "TXN-PS-8849201",
  "amountPaid": 7500.00,
  "paymentMethod": "BUDGET_LEDGER",
  "departmentBudget": { "remaining": 142500.00, "reserved": 0.00, "spent": 7500.00 },
  "poStatus": "COMPLETED",
  "paidAt": "2026-11-18T11:00:00Z"
}
```

**Error Responses:**
* `400 Bad Request`: PO is not in ISSUED status.
* `403 Forbidden`: User lacks `ROLE_PROCUREMENT_ADMIN`, or is the PO requester (`SEPARATION_OF_DUTIES`).
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
    "title": "SLA Auto-Renewal Notice Window (60 Days)",
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