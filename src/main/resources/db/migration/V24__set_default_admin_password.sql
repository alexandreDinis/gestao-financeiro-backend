-- V24__set_default_admin_password.sql
-- senha: admin123
UPDATE usuario SET senha = '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.7u41W3G' WHERE email = 'admin@financeiro.com';
