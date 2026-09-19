CREATE TABLE marca (
    id SERIAL PRIMARY KEY,
    cod_marca VARCHAR(10) NOT NULL,
    descr_marca VARCHAR(100) NOT NULL,
    CONSTRAINT uk_marca_cod UNIQUE (cod_marca)
);

CREATE TABLE cargo (
    id SERIAL PRIMARY KEY,
    cod_cargo VARCHAR(10) NOT NULL,
    descr_cargo VARCHAR(100) NOT NULL,
    CONSTRAINT uk_cargo_cod UNIQUE (cod_cargo)
);

CREATE TABLE funcionario (
    id SERIAL PRIMARY KEY,
    matricula VARCHAR(20) NOT NULL,
    data_admissao DATE NOT NULL,
    data_demissao DATE,
    CONSTRAINT uk_funcionario_matricula UNIQUE (matricula)
);

CREATE TABLE loja (
    id SERIAL PRIMARY KEY,
    cod_loja VARCHAR(10) NOT NULL,
    descr_loja VARCHAR(100) NOT NULL,
    marca_id INT NOT NULL,
    CONSTRAINT uk_loja_cod UNIQUE (cod_loja),
    CONSTRAINT fk_loja_marca FOREIGN KEY (marca_id) REFERENCES marca (id)
);

-- pct_comiss: numero percentual literal (2.50 = 2,5%), nunca fracao.
-- Normalizacao obrigatoria na importacao: cod_cargo=150 usa so a linha
-- "GERENTE DE LOJA" da planilha; "GERENTE QUIOSQUE" e descartada (decisao do cliente).
CREATE TABLE marca_cargo (
     marca_id INT NOT NULL,
     cargo_id INT NOT NULL,
     date_ref DATE NOT NULL,
     pct_comiss NUMERIC(5,2) NOT NULL,
     PRIMARY KEY (marca_id, cargo_id, date_ref),
     CONSTRAINT fk_marca_cargo_marca FOREIGN KEY (marca_id) REFERENCES marca (id),
     CONSTRAINT fk_marca_cargo_cargo FOREIGN KEY (cargo_id) REFERENCES cargo (id)
);

CREATE TABLE funcionario_loja (
    funcionario_id INT NOT NULL,
    loja_id INT NOT NULL,
    date_ref DATE NOT NULL,
    PRIMARY KEY (funcionario_id, loja_id, date_ref),
    CONSTRAINT fk_funcionario_loja_funcionario FOREIGN KEY (funcionario_id) REFERENCES funcionario (id),
    CONSTRAINT fk_funcionario_loja_loja FOREIGN KEY (loja_id) REFERENCES loja (id)
);

CREATE TABLE funcionario_cargo (
    funcionario_id INT NOT NULL,
    cargo_id INT NOT NULL,
    date_ref DATE NOT NULL,
    PRIMARY KEY (funcionario_id, cargo_id, date_ref),
    CONSTRAINT fk_funcionario_cargo_funcionario FOREIGN KEY (funcionario_id) REFERENCES funcionario (id),
    CONSTRAINT fk_funcionario_cargo_cargo FOREIGN KEY (cargo_id) REFERENCES cargo (id)
);

CREATE TABLE venda (
    id SERIAL PRIMARY KEY,
    funcionario_id INT NOT NULL,
    date_ref DATE NOT NULL,
    vlr_venda NUMERIC(10,2) NOT NULL,
    CONSTRAINT fk_venda_funcionario FOREIGN KEY (funcionario_id) REFERENCES funcionario (id)
);

-- Indices de apoio: join venda -> funcionario_loja por competencia e agregacoes por funcionario/mes.
CREATE INDEX idx_venda_funcionario_date_ref ON venda (funcionario_id, date_ref);
CREATE INDEX idx_funcionario_loja_funcionario_date_ref ON funcionario_loja (funcionario_id, date_ref);
CREATE INDEX idx_funcionario_cargo_funcionario_date_ref ON funcionario_cargo (funcionario_id, date_ref);