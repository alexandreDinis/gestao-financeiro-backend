-- V31: Permite pausar dívida inadimplente da Previsão de Caixa sem desativar a cobrança
ALTER TABLE divida
    ADD COLUMN IF NOT EXISTS pausar_previsao BOOLEAN NOT NULL DEFAULT FALSE;
