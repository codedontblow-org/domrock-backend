CREATE TABLE solicitacao_alteracao (
    id SERIAL PRIMARY KEY,
    tipo VARCHAR(30) NOT NULL,
    matricula VARCHAR(20) NOT NULL,
    competencia DATE NOT NULL,
    valor_antes JSONB NOT NULL,
    valor_depois JSONB NOT NULL,
    motivo TEXT,
    autor_id INT NOT NULL REFERENCES usuario(id),
    origem VARCHAR(20) NOT NULL,
    chat_id VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    decisor_id INT REFERENCES usuario(id),
    decidida_em TIMESTAMP,
    justificativa TEXT,
    criada_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_solicitacao_status CHECK (status IN ('PENDENTE', 'APROVADA', 'REPROVADA')),
    CONSTRAINT ck_solicitacao_origem CHECK (origem IN ('USUARIO', 'LANA'))
);

CREATE INDEX idx_solicitacao_status ON solicitacao_alteracao (status);
CREATE INDEX idx_solicitacao_mat_comp ON solicitacao_alteracao (matricula, competencia);
