-- V8__create_transacao_recorrente.sql

CREATE TABLE IF NOT EXISTS transacao_recorrente (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    descricao VARCHAR(500) NOT NULL,
    valor NUMERIC(19, 2) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    periodicidade VARCHAR(50) NOT NULL,
    data_inicio DATE NOT NULL,
    data_fim DATE,
    dia_vencimento INTEGER,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    categoria_id BIGINT REFERENCES categoria(id),
    conta_id BIGINT REFERENCES conta(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_recorrente_tenant ON transacao_recorrente(tenant_id);
