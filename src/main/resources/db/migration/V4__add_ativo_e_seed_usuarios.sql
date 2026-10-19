ALTER TABLE usuario ADD COLUMN ativo BOOLEAN DEFAULT TRUE NOT NULL;

INSERT INTO usuario (nome, email, senha, perfil, ativo) VALUES
('Gabriela', 'gabriela@camplana.com.br', '$2a$10$6Nm9z0bqXZeNPicMq.Pux.8vISzELW1lIDGMHQA10y40Z1b.XLon.', 'GERENTE',    true),
('Sérgio',   'sergio@camplana.com.br',   '$2a$10$6Nm9z0bqXZeNPicMq.Pux.8vISzELW1lIDGMHQA10y40Z1b.XLon.', 'SUPERVISOR', true),
('Sílvia',   'silvia@camplana.com.br',   '$2a$10$6Nm9z0bqXZeNPicMq.Pux.8vISzELW1lIDGMHQA10y40Z1b.XLon.', 'SUPERVISOR', true),
('Ana',      'ana@camplana.com.br',      '$2a$10$6Nm9z0bqXZeNPicMq.Pux.8vISzELW1lIDGMHQA10y40Z1b.XLon.', 'ADMIN',      true);