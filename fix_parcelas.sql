WITH invalid_parcelas AS (
    SELECT p.id as parcela_id, p.divida_id, p.valor
    FROM parcela_divida p
    JOIN divida d ON p.divida_id = d.id
    WHERE d.recorrente = true AND p.data_vencimento < d.data_inicio
),
update_dividas AS (
    UPDATE divida d
    SET valor_total = d.valor_total - ip.valor,
        valor_restante = d.valor_restante - ip.valor
    FROM invalid_parcelas ip
    WHERE d.id = ip.divida_id
)
DELETE FROM parcela_divida
WHERE id IN (SELECT parcela_id FROM invalid_parcelas);
