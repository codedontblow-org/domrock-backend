-- A6-64: campanha salva, versões congeladas a cada submissão e log de toda mudança de status.
-- Sem FK para simulacao: a tabela nasce na V8 (A6-66), com simulacao.campanha_id apontando para cá.
-- Sem GRANT para lana_leitura: a Lana recebe as campanhas pelo backend (contrato 4.3).

CREATE TABLE campanha (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    autor_id INT NOT NULL,
    chat_id VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    criada_em TIMESTAMPTZ NOT NULL,
    atualizada_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_campanha_autor FOREIGN KEY (autor_id) REFERENCES usuario (id),
    -- uma conversa, uma campanha (contrato 5.3)
    CONSTRAINT uk_campanha_chat UNIQUE (chat_id),
    CONSTRAINT ck_campanha_status CHECK (status IN
        ('RASCUNHO', 'AGUARDANDO_APROVACAO', 'APROVADA', 'REPROVADA', 'ARQUIVADA'))
);

CREATE INDEX idx_campanha_autor ON campanha (autor_id);

-- Imutável: só recebe INSERT. O status e a decisão de cada versão saem do log (versao_id).
CREATE TABLE campanha_versao (
    id BIGSERIAL PRIMARY KEY,
    campanha_id BIGINT NOT NULL,
    numero INT NOT NULL,
    simulacao_id BIGINT NOT NULL,
    regra JSONB NOT NULL,
    resultado JSONB NOT NULL,
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    -- até onde vai a conversa congelada; nulo até a tabela mensagem_chat (A6-65)
    ultima_mensagem_id BIGINT,
    submetida_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_versao_campanha FOREIGN KEY (campanha_id) REFERENCES campanha (id),
    CONSTRAINT uk_versao_numero UNIQUE (campanha_id, numero)
);

-- A6-68 e A6-74 procuram as versões que cruzam um período
CREATE INDEX idx_versao_periodo ON campanha_versao (data_inicio, data_fim);

CREATE TABLE campanha_status_log (
    id BIGSERIAL PRIMARY KEY,
    campanha_id BIGINT NOT NULL,
    -- preenchido no submeter e no decidir
    versao_id BIGINT,
    -- nulo na criação
    de VARCHAR(30),
    para VARCHAR(30) NOT NULL,
    usuario_id INT NOT NULL,
    em TIMESTAMPTZ NOT NULL,
    justificativa TEXT,
    CONSTRAINT fk_status_log_campanha FOREIGN KEY (campanha_id) REFERENCES campanha (id),
    CONSTRAINT fk_status_log_versao FOREIGN KEY (versao_id) REFERENCES campanha_versao (id),
    CONSTRAINT fk_status_log_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);

CREATE INDEX idx_status_log_campanha ON campanha_status_log (campanha_id);
