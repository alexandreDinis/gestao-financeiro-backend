-- V11__create_parcela.sql

CREATE TABLE IF NOT EXISTS parcela (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    transacao_id BIGINT NOT NULL REFERENCES transacao(id),
    fatura_id BIGINT NOT NULL REFERENCES fatura_cartao(id),
    numero_parcela INTEGER NOT NULL,
    total_parcelas INTEGER NOT NULL,
    valor_parcela NUMERIC(19, 2) NOT NULL,
    data_vencimento DATE NOT NULL,
    paga BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_parcela_tenant ON parcela(tenant_id);
CREATE INDEX idx_parcela_fatura ON parcela(fatura_id);
CREATE INDEX idx_parcela_transacao ON parcela(transacao_id);
