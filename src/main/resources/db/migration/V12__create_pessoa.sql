-- V12__create_pessoa.sql

CREATE TABLE IF NOT EXISTS pessoa (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    nome VARCHAR(255) NOT NULL,
    telefone VARCHAR(50),
    observacao TEXT,
    score_confiabilidade VARCHAR(50) NOT NULL DEFAULT 'REGULAR',
    total_emprestimos INTEGER NOT NULL DEFAULT 0,
    total_pagos_em_dia INTEGER NOT NULL DEFAULT 0,
    total_atrasados INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_pessoa_tenant ON pessoa(tenant_id);
