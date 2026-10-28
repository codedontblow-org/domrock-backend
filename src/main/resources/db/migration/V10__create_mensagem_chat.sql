-- A6-65: histórico de conversa salvo no banco, sobrevivendo a reinício dos containers.
-- O backend grava as mensagens do usuário e da Lana, e mensagens de sistema de status e simulação.

CREATE TABLE mensagem_chat (
    id BIGSERIAL PRIMARY KEY,
    chat_id VARCHAR(64) NOT NULL,
    usuario_id INT NOT NULL,
    papel VARCHAR(20) NOT NULL,
    tipo VARCHAR(20),
    simulacao_id BIGINT,
    texto TEXT NOT NULL,
    ficha JSONB,
    trilha JSONB,
    criada_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_mensagem_chat_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT fk_mensagem_chat_simulacao FOREIGN KEY (simulacao_id) REFERENCES simulacao (id),
    CONSTRAINT ck_mensagem_chat_papel CHECK (papel IN ('usuario', 'lana', 'sistema')),
    CONSTRAINT ck_mensagem_chat_tipo CHECK (tipo IS NULL OR tipo IN ('STATUS', 'SIMULACAO'))
);

CREATE INDEX idx_mensagem_chat_chat ON mensagem_chat (chat_id, criada_em, id);

ALTER TABLE campanha_versao
    ADD CONSTRAINT fk_versao_ultima_mensagem FOREIGN KEY (ultima_mensagem_id) REFERENCES mensagem_chat (id);
