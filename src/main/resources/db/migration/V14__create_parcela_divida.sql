-- V14__create_parcela_divida.sql

CREATE TABLE IF NOT EXISTS parcela_divida (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    divida_id BIGINT NOT NULL REFERENCES divida(id),
    numero_parcela INTEGER NOT NULL,
    valor NUMERIC(19, 2) NOT NULL,
    data_vencimento DATE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDENTE',
    data_pagamento DATE,
    transacao_id BIGINT REFERENCES transacao(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_pd_tenant ON parcela_divida(tenant_id);
CREATE INDEX idx_pd_divida ON parcela_divida(divida_id);
CREATE INDEX idx_pd_vencimento ON parcela_divida(tenant_id, data_vencimento);
