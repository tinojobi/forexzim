# ZimRate Deep Browser Check

## Purpose

Provide a heavier, manual browser QA pass for ZimRate after meaningful publishes, frontend changes, or smoke-check failures.

Unlike the lightweight smoke suite, this pass is meant to answer:
- does the public UI still look clean in a real browser?
- are there visible layout or rendering defects?
- are the main public surfaces free of browser-console errors?
- does the latest article still look publish-safe, including share and signup surfaces?

## Implemented artifact

Script:

```bash
/opt/forexzim/monitor/zimrate_browser_deep_check.py
```

Hermes wrapper:

```bash
/root/.hermes/scripts/zimrate_browser_deep_check.py
```

Runtime:

```bash
/opt/forexzim/.venv-monitor
```

## Included modes

### 1. Publish-grade latest-article checklist
Checks the newest article discovered from `/blog` for:
- page render success
- matching canonical URL
- exactly one `og:image`
- exactly one `twitter:image`
- matching social-image values
- visible hero image
- visible share controls
- visible email signup block
- visible related articles
- zero browser console errors

### 2. Responsive deep check
Captures the latest article at:
- desktop
- tablet
- mobile

This is aimed at quickly catching breakpoint regressions without building a full visual regression system.

### 3. Evidence-pack mode
Each run creates a timestamped evidence pack with:
- full-page screenshots
- markdown report
- JSON result payload

## Scope

### Homepage visual + console check
Checks `/` for:
- clean hero rendering
- visible rate cards and tables
- no overlap, clipping, or broken sections
- no browser console errors
- successful opening of the `Set a rate alert` modal

### Blog index visual + content-card check
Checks `/blog` for:
- no browser console errors
- recent article cards render consistently
- cards have thumbnails with non-empty alt text
- no obvious gaps or broken layout

### Latest article responsive + metadata check
Checks the newest article linked from `/blog` for:
- no browser console errors
- clean article page layout across breakpoints
- hero image renders
- share controls are visible
- email signup block is visible and usable
- related articles render correctly
- `og:image` and `twitter:image` exist and match
- canonical URL is present and matches the page URL

## Outputs

Latest report:

```bash
/opt/forexzim/monitor/zimrate_browser_deep_check_latest.md
```

Timestamped evidence packs:

```bash
/opt/forexzim/monitor/browser-deep-checks/
```

Latest copied evidence pack:

```bash
/opt/forexzim/monitor/browser-deep-checks/latest/
```

## Usage

Run directly:

```bash
cd /opt/forexzim
. .venv-monitor/bin/activate
python monitor/zimrate_browser_deep_check.py
```

Run via Hermes wrapper:

```bash
python3 /root/.hermes/scripts/zimrate_browser_deep_check.py
```

JSON output:

```bash
python3 /root/.hermes/scripts/zimrate_browser_deep_check.py --json
```

## When to use it

Recommended manual triggers:
- after publishing a major article
- after frontend/layout changes
- after metadata/social-image fixes
- immediately after a smoke alert
- before sharing a newly published article widely

## Relationship to the smoke suite

- `zimrate_browser_smoke.py` = frequent watchdog, cheap, mostly structural
- `zimrate_browser_deep_check.py` = manual, slower, screenshot-backed, visual + console-focused

Use the smoke suite for routine monitoring.
Use the deep browser check when you want confidence, not just reachability.
