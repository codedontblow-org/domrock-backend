-- A6-66: simulação como job assíncrono com status e registro de toda simulação.

CREATE TABLE simulacao (
    id BIGSERIAL PRIMARY KEY,
    usuario_id INT NOT NULL,
    chat_id VARCHAR(64) NOT NULL,
    campanha_id BIGINT,
    status VARCHAR(20) NOT NULL,
    etapa VARCHAR(50),
    ficha JSONB NOT NULL,
    resultado JSONB,
    trilha JSONB,
    codigo_gerado TEXT,
    modelo VARCHAR(100),
    tentativas INT,
    duracao_ms BIGINT,
    erro TEXT,
    criada_em TIMESTAMPTZ NOT NULL,
    concluida_em TIMESTAMPTZ,
    CONSTRAINT fk_simulacao_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT fk_simulacao_campanha FOREIGN KEY (campanha_id) REFERENCES campanha (id),
    CONSTRAINT ck_simulacao_status CHECK (status IN ('PENDENTE', 'RODANDO', 'CONCLUIDA', 'ERRO'))
);

CREATE INDEX idx_simulacao_chat ON simulacao (chat_id);
CREATE INDEX idx_simulacao_campanha ON simulacao (campanha_id);
CREATE INDEX idx_simulacao_status ON simulacao (status);

ALTER TABLE campanha_versao
    ADD CONSTRAINT fk_versao_simulacao FOREIGN KEY (simulacao_id) REFERENCES simulacao (id);
