CREATE TABLE deliberation
(
    id                     CHAR(32) PRIMARY KEY NOT NULL,
    libelle                VARCHAR(255)         NOT NULL,
    deliberation_date      DATE                 NOT NULL,
    creation_date          TIMESTAMP            NOT NULL,
    last_modification_date TIMESTAMP            NOT NULL
);

