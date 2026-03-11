-- V5__create_transacao_lancamento.sql

CREATE TABLE IF NOT EXISTS transacao (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    descricao VARCHAR(500) NOT NULL,
    valor NUMERIC(19, 2) NOT NULL,
    data DATE NOT NULL,
    data_vencimento DATE,
    data_pagamento DATE,
    tipo VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDENTE',
    observacao TEXT,
    idempotency_key VARCHAR(255) UNIQUE,
    categoria_id BIGINT REFERENCES categoria(id),
    usuario_id BIGINT REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_transacao_tenant_data ON transacao(tenant_id, data);
CREATE INDEX idx_transacao_tenant_categoria ON transacao(tenant_id, categoria_id);
CREATE INDEX idx_transacao_tenant_status ON transacao(tenant_id, status);
CREATE INDEX idx_transacao_idempotency ON transacao(idempotency_key);

-- Lançamento contábil (ledger entry) - IMUTÁVEL
CREATE TABLE IF NOT EXISTS lancamento (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    transacao_id BIGINT NOT NULL REFERENCES transacao(id),
    conta_id BIGINT NOT NULL REFERENCES conta(id),
    valor NUMERIC(19, 2) NOT NULL,
    direcao VARCHAR(10) NOT NULL,
    descricao VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_lancamento_tenant_conta ON lancamento(tenant_id, conta_id);
CREATE INDEX idx_lancamento_conta_direcao ON lancamento(conta_id, direcao);
CREATE INDEX idx_lancamento_transacao ON lancamento(transacao_id);
