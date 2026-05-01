CREATE TABLE previsao_ajuste (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    mes INT NOT NULL,
    ano INT NOT NULL,
    ajuste_entrada NUMERIC(19, 2) DEFAULT 0.00,
    ajuste_saida NUMERIC(19, 2) DEFAULT 0.00,
    CONSTRAINT uk_previsao_ajuste_tenant_mes_ano UNIQUE (tenant_id, mes, ano)
);

CREATE INDEX idx_previsao_ajuste_tenant ON previsao_ajuste(tenant_id);
