ALTER TABLE divida ADD COLUMN IF NOT EXISTS categoria_id BIGINT REFERENCES categoria(id);
CREATE INDEX IF NOT EXISTS idx_divida_categoria ON divida(categoria_id);
