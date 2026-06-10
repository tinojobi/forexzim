# ZimRate Browser Smoke Suite

## Purpose

Provide a fast, repeatable sanity check for ZimRate's public surfaces so obvious breakage is caught before or shortly after publishing.

This is a **smoke suite**, not full regression coverage. It answers:
- does the homepage still render the core exchange-rate experience?
- does the blog index still expose recent articles?
- does a representative live article still render correctly?
- do critical public routes and article social tags still look publish-safe?

## Scope

### 1. Homepage smoke
Check that `/`:
- returns HTTP 200
- has the expected homepage title / H1
- exposes core nav links: History, Blog, About
- exposes core public actions like `Set a rate alert`
- exposes key rate text such as `OFFICIAL RATE` and `BLACK MARKET MAX`

### 2. Blog index smoke
Check that `/blog`:
- returns HTTP 200
- has the blog title / heading
- exposes recent article links
- exposes at least 3 article cards/links so the listing is not blank

### 3. Representative article smoke
Check the most recent article discovered from `/blog`:
- returns HTTP 200
- has a non-empty `<title>`
- has a single article H1-like heading signal
- contains basic article-body text and author/read-time markers
- exposes exactly one `og:image` and one `twitter:image`
- uses an article-specific social image rather than the default fallback
- serves that social image with HTTP 200 and `image/*` content type

### 4. Critical route smoke
Check that key public routes are still reachable:
- `/history`
- `/convert/100-usd-to-zig`

## What this suite intentionally does not cover

Not included in the smoke layer:
- deep visual-diff checks
- full accessibility auditing
- full SEO audits
- content fact-checking
- newsletter / form submission end-to-end
- browser-console checks across all routes on every cron tick

Those remain better suited to manual browser QA sessions or a heavier scheduled browser pass.

## Operational use

### Manual report mode
Run the smoke suite and always print a full report:

```bash
cd /opt/forexzim
python3 monitor/zimrate_browser_smoke.py --report
```

This also writes the latest markdown report to:

```bash
/opt/forexzim/monitor/zimrate_browser_smoke_latest.md
```

### Watchdog mode
Run without flags for silent-on-healthy watchdog behavior:

```bash
cd /opt/forexzim
python3 monitor/zimrate_browser_smoke.py
```

Behavior:
- healthy and unchanged: prints nothing
- failure: prints alert summary
- recovery after prior failure: prints recovery summary

## Recommended cadence

- **manual**: before or after important publishes
- **scheduled watchdog**: every 4 hours during the day is enough for public-surface smoke without being noisy

## Follow-up path when smoke fails

1. inspect the generated markdown report
2. open the affected route in browser QA mode
3. check console errors and visible layout
4. if article-specific metadata failed, inspect social-image tags and publish helper behavior
5. fix, rerun `--report`, then confirm the watchdog returns to healthy
