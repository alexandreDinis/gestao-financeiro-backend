-- V6__create_orcamento.sql

CREATE TABLE IF NOT EXISTS orcamento (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    limite NUMERIC(19, 2) NOT NULL,
    mes INTEGER NOT NULL,
    ano INTEGER NOT NULL,
    categoria_id BIGINT NOT NULL REFERENCES categoria(id),
    usuario_id BIGINT REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_orcamento_categoria_periodo UNIQUE (tenant_id, categoria_id, mes, ano)
);

CREATE INDEX idx_orcamento_tenant ON orcamento(tenant_id);
CREATE INDEX idx_orcamento_periodo ON orcamento(tenant_id, mes, ano);
