-- V4__create_categoria.sql

CREATE TABLE IF NOT EXISTS categoria (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    nome VARCHAR(255) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    cor VARCHAR(20),
    icone VARCHAR(50),
    categoria_pai_id BIGINT REFERENCES categoria(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_categoria_tenant ON categoria(tenant_id);
CREATE INDEX idx_categoria_pai ON categoria(categoria_pai_id);

-- Seed de categorias padrão para tenant_id = 1
INSERT INTO categoria (tenant_id, nome, tipo, cor, icone) VALUES
    (1, 'Salário', 'RECEITA', '#10B981', 'wallet'),
    (1, 'Freelance', 'RECEITA', '#3B82F6', 'briefcase'),
    (1, 'Investimentos', 'RECEITA', '#8B5CF6', 'trending-up'),
    (1, 'Outros Receitas', 'RECEITA', '#6B7280', 'plus-circle'),
    (1, 'Alimentação', 'DESPESA', '#EF4444', 'utensils'),
    (1, 'Transporte', 'DESPESA', '#F59E0B', 'car'),
    (1, 'Moradia', 'DESPESA', '#3B82F6', 'home'),
    (1, 'Saúde', 'DESPESA', '#EC4899', 'heart'),
    (1, 'Educação', 'DESPESA', '#8B5CF6', 'book'),
    (1, 'Lazer', 'DESPESA', '#F97316', 'gamepad'),
    (1, 'Vestuário', 'DESPESA', '#14B8A6', 'shirt'),
    (1, 'Contas Fixas', 'DESPESA', '#6366F1', 'file-text'),
    (1, 'Outros Despesas', 'DESPESA', '#6B7280', 'more-horizontal');
