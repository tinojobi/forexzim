CREATE TABLE telegram_alerts (
    id               BIGSERIAL     PRIMARY KEY,
    chat_id          BIGINT        NOT NULL,
    username         VARCHAR(100),
    first_name       VARCHAR(100),
    currency_pair    VARCHAR(30)   NOT NULL,
    direction        VARCHAR(10)   NOT NULL,
    threshold        NUMERIC(18,4) NOT NULL,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    last_notified_at TIMESTAMP
);

CREATE INDEX idx_telegram_alerts_chat_id ON telegram_alerts(chat_id);
CREATE INDEX idx_telegram_alerts_active  ON telegram_alerts(active);
