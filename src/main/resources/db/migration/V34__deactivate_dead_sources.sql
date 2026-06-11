-- Deactivate scrape sources confirmed dead on 2026-06-11:
--  * RBZ      — site is behind Radware bot protection; every request gets a
--               JS challenge page, so server-side scraping cannot succeed.
--               The official rate is already covered via ZimPriceCheck.
--  * ZimRates — zimrates.com no longer exists; the domain now serves an
--               unrelated sports-betting site. (Was already inactive; kept
--               here for the record.)
UPDATE sources SET active = FALSE WHERE name IN ('RBZ', 'ZimRates');
