-- Seed default data sources. ON CONFLICT makes this safe to re-run on
-- existing databases that already have these rows.
INSERT INTO sources (name, type, url, active) VALUES
    ('RBZ',              'official',        'https://www.rbz.co.zw',                                                                     true),
    ('CBZ',              'bank',            'https://www.cbz.co.zw',                                                                     true),
    ('ZimRates',         'aggregator',      'https://www.zimrates.com',                                                                  false),
    ('Exchange Rate API','official_api',    'https://api.exchangerate-api.com/v4/latest/USD',                                            true),
    ('FBC Bank',         'bank',            'https://fbc.co.zw/microfinance/forex-rates',                                               true),
    ('ZimPriceCheck',    'parallel_market', 'https://zimpricecheck.com/price-updates/official-and-black-market-exchange-rates/',         true)
ON CONFLICT (name) DO NOTHING;
