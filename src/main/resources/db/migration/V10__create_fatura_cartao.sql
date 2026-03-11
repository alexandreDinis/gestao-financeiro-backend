-- V10__create_fatura_cartao.sql

CREATE TABLE IF NOT EXISTS fatura_cartao (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    cartao_id BIGINT NOT NULL REFERENCES cartao_credito(id),
    mes_referencia INTEGER NOT NULL,
    ano_referencia INTEGER NOT NULL,
    valor_total NUMERIC(19, 2) NOT NULL DEFAULT 0,
    data_vencimento DATE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ABERTA',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_fatura_periodo UNIQUE (cartao_id, mes_referencia, ano_referencia)
);

CREATE INDEX idx_fatura_tenant ON fatura_cartao(tenant_id);
CREATE INDEX idx_fatura_cartao_periodo ON fatura_cartao(cartao_id, mes_referencia, ano_referencia);
