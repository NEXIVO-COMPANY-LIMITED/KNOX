# Database Schema & Data Architecture (PostgreSQL)

## 1. Relational Database Overview
The MDM backend uses PostgreSQL for core data persistence, leveraging JSONB for flexible policy structures, Flyway for schema migrations, and column-level encryption for PII and hardware identifiers.

---

## 2. DDL & Core Table Specifications

### 2.1 `stores` (Retail Stores & License Locations)
```sql
CREATE TABLE stores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_license_id VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    location VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### 2.2 `loans` (Customer Financing & KYC Registry)
```sql
CREATE TABLE loans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_number VARCHAR(128) NOT NULL UNIQUE, -- e.g. 'LN-2026-08912'
    customer_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(64) NOT NULL,
    national_id_nida VARCHAR(128) NOT NULL,
    email VARCHAR(128),
    physical_address TEXT NOT NULL,
    occupation VARCHAR(128),
    guarantor_name VARCHAR(255),
    guarantor_phone VARCHAR(64),
    status VARCHAR(64) NOT NULL DEFAULT 'KYC_SUBMITTED', -- 'KYC_SUBMITTED', 'PAYMENT_CONFIRMED', 'ACTIVE', 'COMPLETED'
    down_payment_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### 2.3 `devices` (Asset Registry linked to Stores & Loans)
```sql
CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id UUID REFERENCES stores(id),
    loan_id UUID REFERENCES loans(id),
    agent_id VARCHAR(128) NOT NULL,
    oem_id VARCHAR(64) NOT NULL DEFAULT 'SAMSUNG',
    serial_number VARCHAR(128) NOT NULL,
    imei VARCHAR(256) NOT NULL UNIQUE, -- Column-level encrypted PII
    ownership_state VARCHAR(64) NOT NULL DEFAULT 'DEVICE_OWNER',
    status VARCHAR(64) NOT NULL DEFAULT 'PREREGISTERED', -- 'PREREGISTERED', 'KME_QUEUED', 'PROVISIONED', 'LOCKED'
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### 2.4 `device_credentials` (Auth Key Storage)
```sql
CREATE TABLE device_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    credential_type VARCHAR(64) NOT NULL,
    public_key_or_cert TEXT NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);
```

### 2.5 `enrollment_tokens` (Single-Use Token Storage)
```sql
CREATE TABLE enrollment_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(256) NOT NULL UNIQUE,
    single_use BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(128) NOT NULL
);
```

### 2.6 `commands` & `command_results` (Durable Command Queue)
```sql
CREATE TABLE commands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id),
    type VARCHAR(64) NOT NULL, -- 'LOCK', 'WIPE', 'REBOOT', 'SYNC', 'KIOSK_ON', 'KIOSK_OFF'
    payload JSONB DEFAULT '{}'::jsonb,
    status VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    CONSTRAINT uq_device_idempotency UNIQUE (device_id, idempotency_key)
);

CREATE TABLE command_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    command_id UUID NOT NULL UNIQUE REFERENCES commands(id),
    device_id UUID NOT NULL REFERENCES devices(id),
    result_status VARCHAR(64) NOT NULL,
    result_payload JSONB DEFAULT '{}'::jsonb,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reported_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### 2.7 `audit_logs` (Append-Only Immutable Audit Trail)
```sql
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_type VARCHAR(64) NOT NULL, -- 'DEVICE', 'AGENT', 'OPERATOR', 'SYSTEM'
    actor_id VARCHAR(128) NOT NULL,
    action VARCHAR(128) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    payload JSONB DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Immutability Enforcement
CREATE OR REPLACE FUNCTION block_audit_log_edits()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit log entries are immutable and cannot be updated or deleted.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_immutable
BEFORE UPDATE OR DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION block_audit_log_edits();
```

### 2.8 `oem_integration_data`
```sql
CREATE TABLE oem_integration_data (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    oem_id VARCHAR(64) NOT NULL DEFAULT 'SAMSUNG',
    capability_status JSONB DEFAULT '{}'::jsonb,
    license_status VARCHAR(64),
    oem_version VARCHAR(64),
    last_synced_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```
