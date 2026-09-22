CREATE TABLE usuario (
     id SERIAL PRIMARY KEY,
     nome VARCHAR(100) NOT NULL,
     email VARCHAR(150) NOT NULL,
     senha VARCHAR(255) NOT NULL,
     perfil VARCHAR(30) NOT NULL,
     CONSTRAINT uk_usuario_email UNIQUE (email)
);