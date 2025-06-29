CREATE TABLE csv_info (
                         id BIGSERIAL PRIMARY KEY,
                         nome VARCHAR(255) not null,
                         sobrenome VARCHAR(255) not null,
                         email VARCHAR(255) not null,
                         sexo CHAR(6) not null,
                         ip_acesso VARCHAR(45) not null,
                         idade INTEGER not null,
                         nascimento DATE not null
);