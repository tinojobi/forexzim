# ZimRate Deep Browser Check Report — 2026-06-10T06:04:08+00:00

- Overall verdict: **FAIL**
- Console errors observed: **0**
- Evidence directory: `/opt/forexzim/monitor/browser-deep-checks/20260610T060241Z`
- Latest article: `https://zimrate.com/blog/rbz-first-zig-bill-auction-benchmark-rate`

## Included modes

- Publish-grade latest-article checklist
- Responsive article pass: desktop, tablet, mobile
- Evidence-pack screenshots

## Checks
- ✅ `homepage` [desktop] — https://zimrate.com/
  - title=1 USD to ZiG Today: 26.92 ZiG | ZimRate
  - screenshot: `/opt/forexzim/monitor/browser-deep-checks/20260610T060241Z/screenshots/homepage_desktop.png`
- ✅ `latest_article` [desktop] — https://zimrate.com/blog/rbz-first-zig-bill-auction-benchmark-rate
  - title=RBZ's First ZiG Bill Auction Sets a Real Benchmark | ZimRate
  - hero_image=https://pub-b56fdc7dd7bc4e99ab5d1daad8a27630.r2.dev/rbz-first-zig-bill-auction-user-social.jpg
  - screenshot: `/opt/forexzim/monitor/browser-deep-checks/20260610T060241Z/screenshots/latest_article_desktop.png`
- ✅ `latest_article` [tablet] — https://zimrate.com/blog/rbz-first-zig-bill-auction-benchmark-rate
  - title=RBZ's First ZiG Bill Auction Sets a Real Benchmark | ZimRate
  - hero_image=https://pub-b56fdc7dd7bc4e99ab5d1daad8a27630.r2.dev/rbz-first-zig-bill-auction-user-social.jpg
  - screenshot: `/opt/forexzim/monitor/browser-deep-checks/20260610T060241Z/screenshots/latest_article_tablet.png`
- ✅ `latest_article` [mobile] — https://zimrate.com/blog/rbz-first-zig-bill-auction-benchmark-rate
  - title=RBZ's First ZiG Bill Auction Sets a Real Benchmark | ZimRate
  - hero_image=https://pub-b56fdc7dd7bc4e99ab5d1daad8a27630.r2.dev/rbz-first-zig-bill-auction-user-social.jpg
  - screenshot: `/opt/forexzim/monitor/browser-deep-checks/20260610T060241Z/screenshots/latest_article_mobile.png`

## Artifacts

- `/opt/forexzim/monitor/browser-deep-checks/20260610T060241Z/screenshots/homepage_desktop.png`
- `/opt/forexzim/monitor/browser-deep-checks/20260610T060241Z/screenshots/latest_article_desktop.png`
- `/opt/forexzim/monitor/browser-deep-checks/20260610T060241Z/screenshots/latest_article_mobile.png`
- `/opt/forexzim/monitor/browser-deep-checks/20260610T060241Z/screenshots/latest_article_tablet.png`

## Failures

- Page.goto: Timeout 20000ms exceeded.
Call log:
  - navigating to "https://zimrate.com/", waiting until "networkidle"

- Page.goto: Timeout 20000ms exceeded.
Call log:
  - navigating to "https://zimrate.com/blog", waiting until "networkidle"

