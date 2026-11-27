-- V30: Evolução de Dívidas para suportar Boletos, Carnês, Financiamentos e Juros/Desconto por parcela
ALTER TABLE divida
    ADD COLUMN IF NOT EXISTS modalidade VARCHAR(30) NOT NULL DEFAULT 'EMPRESTIMO',
    ADD COLUMN IF NOT EXISTS credor VARCHAR(120);

-- Classifica automaticamente dívidas existentes sem pessoa vinculada como BOLETO
UPDATE divida
SET modalidade = 'BOLETO'
WHERE pessoa_id IS NULL AND modalidade = 'EMPRESTIMO';

CREATE INDEX IF NOT EXISTS idx_divida_tenant_modalidade
    ON divida (tenant_id, modalidade);

ALTER TABLE parcela_divida
    ADD COLUMN IF NOT EXISTS linha_digitavel VARCHAR(120),
    ADD COLUMN IF NOT EXISTS valor_juros NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS valor_desconto NUMERIC(19, 2) NOT NULL DEFAULT 0;
