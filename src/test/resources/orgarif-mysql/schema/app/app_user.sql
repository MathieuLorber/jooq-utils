CREATE TABLE app_user
(
    id          CHAR(32) PRIMARY KEY NOT NULL,
    mail        VARCHAR(255) UNIQUE  NOT NULL,
    password    VARCHAR(60)          NOT NULL,
    username    VARCHAR(255) UNIQUE,
    language    VARCHAR(2)           NOT NULL,
    admin       BOOLEAN              NOT NULL,
    signup_date TIMESTAMP            NOT NULL,
    dirty_mail  VARCHAR(255)
);

CREATE INDEX app_user_username_idx ON app_user (username);
CREATE INDEX app_user_mail_idx ON app_user (mail);
