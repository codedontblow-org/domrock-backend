CREATE TABLE importacao (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(30) NOT NULL,
    competencia VARCHAR(7),
    usuario_id INT,
    arquivos JSONB NOT NULL DEFAULT '[]',
    total_linhas INT NOT NULL DEFAULT 0,
    linhas_importadas INT NOT NULL DEFAULT 0,
    totais JSONB NOT NULL DEFAULT '{}',
    pendencias JSONB NOT NULL DEFAULT '[]',
    erro TEXT,
    criada_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    concluida_em TIMESTAMP,
    CONSTRAINT fk_importacao_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);

CREATE INDEX idx_importacao_competencia ON importacao (competencia);
CREATE INDEX idx_importacao_status ON importacao (status);

GRANT SELECT ON importacao TO lana_leitura;
GRANT USAGE, SELECT ON SEQUENCE importacao_id_seq TO lana_leitura;

CREATE UNIQUE INDEX uk_importacao_em_andamento ON importacao (status) WHERE status = 'PROCESSANDO';
