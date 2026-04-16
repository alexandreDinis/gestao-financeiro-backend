CREATE TABLE idempotency_control (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    created_by BIGINT,
    deleted_at TIMESTAMP WITHOUT TIME ZONE,
    version BIGINT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_snapshot TEXT,
    expires_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_idempotency_key ON idempotency_control (idempotency_key);
