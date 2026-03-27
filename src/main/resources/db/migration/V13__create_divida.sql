-- V13__create_divida.sql

CREATE TABLE IF NOT EXISTS divida (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    pessoa_id BIGINT NOT NULL REFERENCES pessoa(id),
    descricao VARCHAR(500) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    valor_total NUMERIC(19, 2) NOT NULL,
    valor_restante NUMERIC(19, 2) NOT NULL,
    data_inicio DATE NOT NULL,
    data_fim DATE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDENTE',
    observacao TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_divida_tenant ON divida(tenant_id);
CREATE INDEX idx_divida_pessoa ON divida(pessoa_id);
