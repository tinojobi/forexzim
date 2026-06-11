-- Browser web-push subscriptions (VAPID)
CREATE TABLE push_subscriptions (
    id         BIGSERIAL PRIMARY KEY,
    endpoint   TEXT         NOT NULL UNIQUE,
    p256dh     VARCHAR(255) NOT NULL,
    auth       VARCHAR(255) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_push_subscriptions_active ON push_subscriptions (active);
