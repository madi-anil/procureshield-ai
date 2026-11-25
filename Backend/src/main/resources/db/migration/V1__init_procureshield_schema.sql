-- 1. Departments & Cost Centers (User Module)
CREATE TABLE departments (
                             id BIGSERIAL PRIMARY KEY,
                             name VARCHAR(100) NOT NULL UNIQUE,
                             cost_center_code VARCHAR(30) NOT NULL UNIQUE,
                             annual_budget   DECIMAL(14, 2) NOT NULL DEFAULT 0.00 CHECK (annual_budget >= 0),
                             remaining_budget DECIMAL(14, 2) NOT NULL DEFAULT 0.00 CHECK (remaining_budget >= 0),
                             reserved_budget DECIMAL(14, 2) NOT NULL DEFAULT 0.00 CHECK (reserved_budget >= 0),
                             spent_budget    DECIMAL(14, 2) NOT NULL DEFAULT 0.00 CHECK (spent_budget >= 0),
                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT chk_budget_invariant
                                 CHECK (annual_budget = remaining_budget + reserved_budget + spent_budget)
);

-- 2. Users & RBAC Roles (Auth & User Module)
CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       department_id BIGINT REFERENCES departments(id) ON DELETE SET NULL,
                       email VARCHAR(120) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       full_name VARCHAR(100) NOT NULL,
                       role VARCHAR(40) NOT NULL DEFAULT 'ROLE_EMPLOYEE',
                       is_active BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2b. Refresh Tokens (Auth Module - Token Rotation & Revocation)
CREATE TABLE refresh_tokens (
                                id BIGSERIAL PRIMARY KEY,
                                user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                token_hash VARCHAR(255) NOT NULL UNIQUE,
                                expires_at TIMESTAMP NOT NULL,
                                revoked BOOLEAN NOT NULL DEFAULT FALSE,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);

-- 3. Vendor SaaS Catalog & AI Risk Scorecard (Product Module)
CREATE TABLE products (
                          id BIGSERIAL PRIMARY KEY,
                          vendor_name VARCHAR(120) NOT NULL,
                          product_name VARCHAR(120) NOT NULL,
                          category VARCHAR(60) NOT NULL,
                          description TEXT,
                          price_per_seat DECIMAL(12, 2) NOT NULL CHECK (price_per_seat >= 0),
                          billing_cycle VARCHAR(20) NOT NULL DEFAULT 'ANNUAL',
                          logo_r2_url VARCHAR(500),
                          soc2_cert_r2_key VARCHAR(255),
                          sla_pdf_r2_key VARCHAR(255),
                          audit_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_UPLOAD',
                          audit_started_at TIMESTAMP,
                          audit_failure_reason VARCHAR(40),
                          ai_risk_score INT CHECK (ai_risk_score BETWEEN 1 AND 100),
                          ai_risk_level VARCHAR(20),
                          ai_risk_scorecard JSONB,
                          renewal_notice_days INT DEFAULT 30,
                          is_active BOOLEAN NOT NULL DEFAULT TRUE,
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Requisition Builder (Cart Module)
CREATE TABLE cart_items (
                            id BIGSERIAL PRIMARY KEY,
                            user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                            seat_quantity INT NOT NULL CHECK (seat_quantity > 0),
                            added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            UNIQUE(user_id, product_id)
);

-- 5. Vendor Shortlist (Wishlist Module)
CREATE TABLE wishlist_items (
                                id BIGSERIAL PRIMARY KEY,
                                user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                                notes VARCHAR(255),
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                UNIQUE(user_id, product_id)
);

-- 6. Purchase Orders & Line Items (Order Module)
CREATE TABLE purchase_orders (
                                 id BIGSERIAL PRIMARY KEY,
                                 po_number VARCHAR(40) NOT NULL UNIQUE,
                                 requester_id BIGINT NOT NULL REFERENCES users(id),
                                 department_id BIGINT NOT NULL REFERENCES departments(id),
                                 approver_id BIGINT REFERENCES users(id),
                                 total_amount DECIMAL(14, 2) NOT NULL CHECK (total_amount >= 0),
                                 highest_ai_risk_score INT,
                                 requires_escalation BOOLEAN NOT NULL DEFAULT FALSE,
                                 status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
                                 justification TEXT,
                                 contract_end_date DATE,
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE purchase_orders ADD CONSTRAINT chk_no_self_approval
    CHECK (approver_id IS NULL OR approver_id <> requester_id);

CREATE TABLE purchase_order_items (
                                      id BIGSERIAL PRIMARY KEY,
                                      purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
                                      product_id BIGINT NOT NULL REFERENCES products(id),
                                      seat_quantity INT NOT NULL CHECK (seat_quantity > 0),
                                      unit_price DECIMAL(12, 2) NOT NULL CHECK (unit_price >= 0),
                                      subtotal DECIMAL(14, 2) NOT NULL CHECK (subtotal >= 0)
);

-- 7. Immutable Compliance Audit Trail (Order Module)
CREATE TABLE po_audit_logs (
                               id BIGSERIAL PRIMARY KEY,
                               purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id) ON DELETE RESTRICT,
                               actor_id BIGINT NOT NULL REFERENCES users(id),
                               previous_status VARCHAR(30),
                               new_status VARCHAR(30) NOT NULL,
                               comments TEXT,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE FUNCTION fn_po_audit_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'po_audit_logs is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_po_audit_no_update_delete
    BEFORE UPDATE OR DELETE ON po_audit_logs
    FOR EACH ROW EXECUTE FUNCTION fn_po_audit_immutable();

CREATE TRIGGER trg_po_audit_no_truncate
    BEFORE TRUNCATE ON po_audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION fn_po_audit_immutable();

-- 8. Corporate Invoice Settlement (Payment Module)
CREATE TABLE payments (
                          id BIGSERIAL PRIMARY KEY,
                          purchase_order_id BIGINT NOT NULL UNIQUE REFERENCES purchase_orders(id),
                          transaction_ref VARCHAR(80) NOT NULL UNIQUE,
                          amount_paid DECIMAL(14, 2) NOT NULL CHECK (amount_paid >= 0),
                          payment_method VARCHAR(40) NOT NULL DEFAULT 'BUDGET_LEDGER',
                          status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
                          paid_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 9. SLA & PO Alerts (Notification Module)
CREATE TABLE notifications (
                               id BIGSERIAL PRIMARY KEY,
                               recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               title VARCHAR(150) NOT NULL,
                               message TEXT NOT NULL,
                               notification_type VARCHAR(40) NOT NULL,
                               is_read BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Domain Constraints (status / role / risk columns)
ALTER TABLE users ADD CONSTRAINT chk_user_role
    CHECK (role IN ('ROLE_EMPLOYEE','ROLE_DEPARTMENT_HEAD','ROLE_PROCUREMENT_ADMIN','ROLE_AUDITOR'));

ALTER TABLE products ADD CONSTRAINT chk_audit_status
    CHECK (audit_status IN ('PENDING_UPLOAD','AUDITING','READY','MANUAL_REVIEW_REQUIRED','FAILED'));

ALTER TABLE products ADD CONSTRAINT chk_risk_level
    CHECK (ai_risk_level IS NULL OR ai_risk_level IN ('LOW','MEDIUM','HIGH'));

ALTER TABLE products ADD CONSTRAINT chk_ready_requires_score
    CHECK (audit_status <> 'READY' OR ai_risk_score IS NOT NULL);

ALTER TABLE purchase_orders ADD CONSTRAINT chk_po_status
    CHECK (status IN ('PENDING_APPROVAL','APPROVED','REJECTED','ISSUED','COMPLETED'));

-- Performance Indexes
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_risk_score ON products(ai_risk_score);
CREATE INDEX idx_products_audit_status ON products(audit_status);
CREATE INDEX idx_po_requester ON purchase_orders(requester_id);
CREATE INDEX idx_po_department ON purchase_orders(department_id);
CREATE INDEX idx_po_status ON purchase_orders(status);
CREATE INDEX idx_notifications_recipient ON notifications(recipient_id, is_read);

-- Initial Seed Data
INSERT INTO departments (name, cost_center_code, annual_budget, remaining_budget) VALUES
                                                                                      ('Platform Engineering', 'CC-ENG-101', 150000.00, 150000.00),
                                                                                      ('Enterprise Security',  'CC-SEC-202',  90000.00,  90000.00);