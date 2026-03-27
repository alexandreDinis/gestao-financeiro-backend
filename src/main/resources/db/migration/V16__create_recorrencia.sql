-- V16__create_recorrencia.sql

CREATE TABLE IF NOT EXISTS recorrencia (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    descricao VARCHAR(500) NOT NULL,
    valor_previsto NUMERIC(19, 2),
    dia_vencimento INTEGER NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    data_inicio DATE NOT NULL,
    data_fim DATE,
    categoria_id BIGINT REFERENCES categoria(id),
    conta_id BIGINT REFERENCES conta(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_recorrencia_tenant ON recorrencia(tenant_id);
