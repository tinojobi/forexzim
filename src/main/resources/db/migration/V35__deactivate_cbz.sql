-- Deactivate CBZ (checked 2026-06-11): the configured URL www.cbz.co.zw is
-- NXDOMAIN — the bank's site moved to cbzbank.co.zw, whose rates page needs
-- a new parser before this source can be re-enabled. Until then every scrape
-- cycle burns 3 failed attempts. Reactivate with:
--   UPDATE sources SET active = TRUE, url = '<new url>' WHERE name = 'CBZ';
UPDATE sources SET active = FALSE WHERE name = 'CBZ';
