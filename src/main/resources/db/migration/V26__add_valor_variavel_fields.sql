ALTER TABLE transacao_recorrente
ADD COLUMN valor_variavel BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE transacao
ADD COLUMN valor_previsto NUMERIC(19, 2);
