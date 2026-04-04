-- V21__make_divida_pessoa_nullable.sql
ALTER TABLE divida ALTER COLUMN pessoa_id DROP NOT NULL;
