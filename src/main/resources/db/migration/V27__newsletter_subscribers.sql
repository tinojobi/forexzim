CREATE TABLE newsletter_subscribers (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    token         VARCHAR(36)  NOT NULL UNIQUE,
    subscribed_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    active        BOOLEAN      NOT NULL DEFAULT TRUE
);

ALTER TABLE blog_posts ADD COLUMN newsletter_notified BOOLEAN NOT NULL DEFAULT FALSE;
