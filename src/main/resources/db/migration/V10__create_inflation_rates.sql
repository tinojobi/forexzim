CREATE TABLE inflation_rates (
    id         BIGSERIAL    PRIMARY KEY,
    rate       NUMERIC(10, 4) NOT NULL,
    period     VARCHAR(30)  NOT NULL UNIQUE,
    scraped_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
