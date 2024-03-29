CREATE TABLE mail_log
(
    id                CHAR(32) PRIMARY KEY NOT NULL,
    application       VARCHAR(255)         NOT NULL,
    deployment_log_id CHAR(32)             NOT NULL,
    recipient_type    VARCHAR(255)         NOT NULL,
    user_id           CHAR(32)             NOT NULL,
    reference         VARCHAR(255)         NOT NULL,
    recipient_mail    VARCHAR(255)         NOT NULL,
    data              TEXT                 NOT NULL,
    subject           TEXT                 NOT NULL,
    content           TEXT                 NOT NULL,
    date              TIMESTAMP            NOT NULL,
    FOREIGN KEY (deployment_log_id) REFERENCES deployment_log (id)
);

CREATE INDEX mail_log_user_id_idx ON mail_log (user_id);