-- Papel somente leitura usado pela Lana (agente de IA). A LLM gera SQL e código a partir de
-- texto do usuário; com este papel, mesmo um SQL que escape do guard da Lana só consegue ler
-- as tabelas de negócio: sem escrita, sem a tabela usuario e sem funções de superusuário
-- (pg_read_file etc.). A senha vem do placeholder do Flyway (LANA_DB_PASSWORD).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lana_leitura') THEN
        CREATE ROLE lana_leitura LOGIN;
    END IF;
END
$$;

ALTER ROLE lana_leitura WITH LOGIN PASSWORD '${lana_db_password}';
ALTER ROLE lana_leitura SET default_transaction_read_only = on;
ALTER ROLE lana_leitura SET statement_timeout = '15s';

GRANT USAGE ON SCHEMA public TO lana_leitura;
GRANT SELECT ON marca, cargo, funcionario, loja, marca_cargo, funcionario_loja, funcionario_cargo, venda
    TO lana_leitura;
