-- V15__create_plano_assinatura.sql

CREATE TABLE IF NOT EXISTS plano (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE,
    tipo VARCHAR(50) NOT NULL UNIQUE,
    preco_mensal NUMERIC(19, 2) NOT NULL,
    max_contas INTEGER NOT NULL,
    max_categorias INTEGER NOT NULL,
    max_cartoes INTEGER NOT NULL,
    max_transacoes_mes INTEGER NOT NULL,
    max_metas INTEGER NOT NULL,
    max_dividas INTEGER NOT NULL,
    max_usuarios INTEGER NOT NULL,
    relatorios_avancados BOOLEAN NOT NULL DEFAULT FALSE,
    projecao_saldo BOOLEAN NOT NULL DEFAULT FALSE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- Seed limits defined in implementation plan
INSERT INTO plano (nome, tipo, preco_mensal, max_contas, max_categorias, max_cartoes, max_transacoes_mes, max_metas, max_dividas, max_usuarios, relatorios_avancados, projecao_saldo)
VALUES 
('Plano Gratuito', 'GRATUITO', 0.00, 2, 10, 1, 100, 2, 5, 1, FALSE, FALSE),
('Plano Básico', 'BASICO', 19.90, 10, 50, 5, 1000, 10, 50, 3, TRUE, FALSE),
('Plano Premium', 'PREMIUM', 39.90, 99999, 99999, 99999, 99999, 99999, 99999, 10, TRUE, TRUE)
ON CONFLICT (tipo) DO NOTHING;

-- As tabelas tenant e assinatura serão criadas com a migration / V1 / V15 logic, but tenant is already in V1.
-- Let's alter tenant to add new fields if needed, and create assinatura.

ALTER TABLE tenant ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'ATIVO';
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS subdominio VARCHAR(255) UNIQUE;

CREATE TABLE IF NOT EXISTS assinatura (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id) UNIQUE,
    plano_id BIGINT NOT NULL REFERENCES plano(id),
    data_inicio DATE NOT NULL,
    data_fim DATE,
    status VARCHAR(50) NOT NULL DEFAULT 'ATIVA',
    valor_mensal NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_assinatura_tenant ON assinatura(tenant_id);

-- Seed Tenant 1 with Gratuito Plan
INSERT INTO assinatura (tenant_id, plano_id, data_inicio, valor_mensal)
SELECT 1, id, CURRENT_DATE, 0.00 FROM plano WHERE tipo = 'GRATUITO'
ON CONFLICT (tenant_id) DO NOTHING;
