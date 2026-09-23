# Database Schema & Data Architecture (PostgreSQL)

## 1. Relational Database Overview
The MDM backend uses PostgreSQL for core data persistence, leveraging JSONB for flexible policy structures, Flyway for schema migrations, and column-level encryption for PII and hardware identifiers.

---

## 2. DDL & Core Table Specifications

### 2.1 `organizations` (Tenancy Root Boundary)
```sql
CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### 2.2 `devices` (Asset Registry)
```sql
CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    enrollment_id UUID,
    oem_id VARCHAR(64) NOT NULL DEFAULT 'SAMSUNG',
    serial_number VARCHAR(128) NOT NULL,
    imei VARCHAR(256), -- Encrypted PII
    android_id VARCHAR(128),
    ownership_state VARCHAR(64) NOT NULL DEFAULT 'DEVICE_OWNER',
    status VARCHAR(64) NOT NULL DEFAULT 'PROVISIONED',
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_devices_org_serial UNIQUE (org_id, serial_number)
);
```

### 2.3 `device_credentials` (Auth Key Storage)
```sql
CREATE TABLE device_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    credential_type VARCHAR(64) NOT NULL,
    public_key_or_cert TEXT NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    rotation_generation INT NOT NULL DEFAULT 1
);
```

### 2.4 `enrollment_tokens` (Single-Use Token Storage)
```sql
CREATE TABLE enrollment_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    token_hash VARCHAR(256) NOT NULL UNIQUE,
    single_use BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(128) NOT NULL
);
```

### 2.5 `policies` & `policy_field_catalog`
```sql
CREATE TABLE policy_field_catalog (
    field_id VARCHAR(128) PRIMARY KEY,
    category VARCHAR(64) NOT NULL, -- 'BASE' or 'OEM'
    required_oem_id VARCHAR(64),   -- e.g. 'SAMSUNG'
    description TEXT
);

CREATE TABLE policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(128) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    policy_document JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### 2.6 `applications` & `application_assignments`
```sql
CREATE TABLE applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    package_name VARCHAR(256) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    source VARCHAR(64) NOT NULL DEFAULT 'MANAGED_PLAY',
    latest_version VARCHAR(64)
);

CREATE TABLE application_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    application_id UUID NOT NULL REFERENCES applications(id),
    target_type VARCHAR(64) NOT NULL, -- 'DEVICE' or 'ORG'
    target_id UUID NOT NULL,
    install_type VARCHAR(64) NOT NULL -- 'REQUIRED', 'ALLOWED', 'BLOCKED'
);
```

### 2.7 `commands` & `command_results` (Durable Command Queue)
```sql
CREATE TABLE commands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    device_id UUID NOT NULL REFERENCES devices(id),
    type VARCHAR(64) NOT NULL,
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

### 2.8 `audit_logs` (Append-Only Immutable Audit Trail)
```sql
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    actor_type VARCHAR(64) NOT NULL, -- 'DEVICE', 'OPERATOR', 'SYSTEM'
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

### 2.9 `oem_integration_data`
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
