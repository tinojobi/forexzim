UPDATE blog_posts
SET
    meta_description = 'ZERA cut Zimbabwe fuel prices April 2026: diesel down to US$2.09/litre, new E20 ethanol blend at US$2.08/litre. What it means for your wallet.',
    content = REPLACE(
        content,
        'check the <a href="https://www.zimrate.com">ZimRate dashboard</a> for the latest parallel and official rates. Fuel is priced in USD, but the ripple effects hit ZiG-denominated wages and prices fast.',
        'check the <a href="https://www.zimrate.com">ZimRate dashboard</a> for the latest parallel and official rates. You can also <a href="https://www.zimrate.com/convert/100-usd-to-zig">convert USD to ZiG</a> to see what your fuel budget is worth in local currency, or <a href="https://www.zimrate.com/history">check the exchange rate history</a> to see how the ZiG has moved against the dollar over time.'
    ),
    updated_at = NOW()
WHERE slug = 'fuel-prices-dropped-sort-of';
