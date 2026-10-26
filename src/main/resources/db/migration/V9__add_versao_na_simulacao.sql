-- A6-68: re-simulação na aprovação (contrato 5.9). A simulação que o Supervisor pede fica ligada à
-- versão que ele re-simulou. Fica sem campanha_id: assim não vira a "última simulação" do autor (5.2),
-- que é a que o próximo submeter congela.

ALTER TABLE simulacao ADD COLUMN versao_id BIGINT;

ALTER TABLE simulacao
    ADD CONSTRAINT fk_simulacao_versao FOREIGN KEY (versao_id) REFERENCES campanha_versao (id);

CREATE INDEX idx_simulacao_versao ON simulacao (versao_id);
