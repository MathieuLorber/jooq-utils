CREATE TABLE magic_link_token
(
    token         VARCHAR(255) PRIMARY KEY NOT NULL,
    user_id       CHAR(32)                 NOT NULL,
    creation_date TIMESTAMP                NOT NULL,
    validity      BOOLEAN                  NOT NULL,
    FOREIGN KEY (user_id) REFERENCES app_user (id)
);