-- Eventos de RH das intercorrencias da especificacao Dom Rock.
-- matricula sem FK: o script nao corrige inconsistências
-- de pendencias lista matriculas que nao batem com a base de RH.
CREATE TABLE evento_rh (
    id SERIAL PRIMARY KEY,
    matricula VARCHAR(20) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    data_inicio DATE,
    data_fim DATE,
    cod_loja VARCHAR(10),
    dias INT,
    origem VARCHAR(255) NOT NULL,
    CONSTRAINT ck_evento_rh_tipo CHECK (tipo IN ('afastamento', 'ferias', 'licenca_maternidade', 'cobertura_loja')),
    CONSTRAINT uk_evento_rh UNIQUE NULLS NOT DISTINCT (matricula, tipo, data_inicio, data_fim, cod_loja, origem)
);

-- Indice para buscas por matricula em consultas
CREATE INDEX idx_evento_rh_matricula ON evento_rh (matricula);

GRANT SELECT ON evento_rh TO lana_leitura;