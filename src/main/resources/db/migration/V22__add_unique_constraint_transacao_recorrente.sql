-- V22__add_unique_constraint_transacao_recorrente.sql
-- Adiciona a restrição única para evitar duplicados de recorrência no mesmo mês/referência.
-- Já tratamos o logicamente excluído (soft delete) no SERVICE para que ele não tente criar se já existir um excluído.
-- Esta constraint serve como o último nível de defesa.

-- Primeiro, limpamos duplicados existentes se houver (para não falhar a migração)
-- Mantemos o mais recente/não excluído de cada (recorrencia_id, referencia)
DELETE FROM transacao t1
WHERE t1.recorrencia_id IS NOT NULL 
  AND t1.referencia IS NOT NULL
  AND t1.id > (
    SELECT min(t2.id) 
    FROM transacao t2 
    WHERE t2.recorrencia_id = t1.recorrencia_id 
      AND t2.referencia = t1.referencia
  );

ALTER TABLE transacao 
    ADD CONSTRAINT uk_transacao_recorrencia_referencia 
    UNIQUE (recorrencia_id, referencia);
