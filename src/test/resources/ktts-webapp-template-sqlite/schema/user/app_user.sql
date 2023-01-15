CREATE TABLE app_user
(
    id UUID PRIMARY KEY,
    mail VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(60) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    language VARCHAR(2) NOT NULL,
    roles VARCHAR(255) NOT NULL,
    signup_date TIMESTAMPTZ NOT NULL,
    last_update TIMESTAMPTZ NOT NULL
);

CREATE INDEX app_user__mail_index ON app_user (mail);
