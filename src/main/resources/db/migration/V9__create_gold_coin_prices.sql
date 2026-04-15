CREATE TABLE gold_coin_prices (
    id         BIGSERIAL PRIMARY KEY,
    price_usd  NUMERIC(18, 4) NOT NULL,
    price_zig  NUMERIC(18, 4),
    valid_date DATE           NOT NULL,
    created_at TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gold_coin_valid_date ON gold_coin_prices (valid_date DESC, created_at DESC);
