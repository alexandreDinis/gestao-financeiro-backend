-- V17__alter_transacao.sql

ALTER TABLE transacao
    ADD COLUMN tipo_despesa VARCHAR(50),
    ADD COLUMN referencia DATE,
    ADD COLUMN gerado_automaticamente BOOLEAN DEFAULT FALSE,
    ADD COLUMN recorrencia_id BIGINT REFERENCES recorrencia(id),
    ADD COLUMN criado_em TIMESTAMP,
    ADD COLUMN atualizado_em TIMESTAMP;
