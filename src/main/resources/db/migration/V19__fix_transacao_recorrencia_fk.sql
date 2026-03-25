-- V19__fix_transacao_recorrencia_fk.sql

-- O erro 23503 ocorre porque a FK aponta para a tabela "recorrencia" 
-- enquanto o código está gerando transações baseadas na tabela "transacao_recorrente".
-- Esta migração unifica a integridade referencial para a tabela que possui os dados ativos.

ALTER TABLE transacao 
    DROP CONSTRAINT IF EXISTS transacao_recorrencia_id_fkey;

ALTER TABLE transacao 
    ADD CONSTRAINT transacao_recorrencia_id_fkey 
    FOREIGN KEY (recorrencia_id) 
    REFERENCES transacao_recorrente(id);
