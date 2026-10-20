-- Eventos de RH das intercorrencias da especificacao Dom Rock.
-- matricula sem FK: o script nao corrige inconsistências
-- o relatório de pendências lista eventuais matrículas que nao nao batem com a base de RH.
-- demissao: data_inicio guarda a data de demissão corrigida pela especificação (set/2025 item h,
-- MATRIC-62); a apuração usa essa data no lugar da cadastrada, sem alterar funcionario (mesma
-- leitura do Synapse, dec-090).
CREATE TABLE evento_rh (
    id SERIAL PRIMARY KEY,
    matricula VARCHAR(20) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    data_inicio DATE,
    data_fim DATE,
    cod_loja VARCHAR(10),
    dias INT,
    origem VARCHAR(255) NOT NULL,
    CONSTRAINT ck_evento_rh_tipo CHECK (tipo IN ('afastamento', 'ferias', 'licenca_maternidade', 'cobertura_loja', 'demissao')),
    CONSTRAINT uk_evento_rh UNIQUE NULLS NOT DISTINCT (matricula, tipo, data_inicio, data_fim, cod_loja, origem)
);

-- Indice para buscas por matricula em consultas
CREATE INDEX idx_evento_rh_matricula ON evento_rh (matricula);

GRANT SELECT ON evento_rh TO lana_leitura;