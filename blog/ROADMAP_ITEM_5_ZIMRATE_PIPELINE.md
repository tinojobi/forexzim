# Roadmap Item 5: ZimRate Pipeline

## Target state

ZimRate pipeline is considered complete when Athena can reliably move a selected economic story through:

1. scheduled source scan,
2. story selection handoff,
3. verified research brief,
4. HTML draft and preview URL,
5. article-specific hero/social image,
6. publish with metadata verification,
7. X post options with validated intent links,
8. novelty log update so the scanner avoids repeated coverage.

## Current state, 2026-05-23

### Operational

- Morning scan cron: `e1a518a0eb6f`, 10:00 CAT, enabled, last status ok.
- Afternoon scan cron: `c93aad45e258`, 17:00 CAT, enabled, last status ok.
- Scanner script: `/opt/forexzim/blog/scan_run.py`.
- Scanner output: `/opt/forexzim/blog/scan_results.json`.
- Research briefs: `/opt/forexzim/blog/briefs/`.
- Article drafts: `/opt/forexzim/blog/drafts/`.
- Image system: `/opt/forexzim/blog/image_system/`.
- X rules: `/opt/forexzim/blog/X_POST_RULES.md`.
- X intent validator: `/opt/forexzim/blog/scripts/x_intent_links.py`.
- Social image verifier: `/opt/forexzim/blog/scripts/verify_social_image.py`.
- Publish helper: `/opt/forexzim/blog/scripts/zimrate_publish_helper.py`.

### Hardened today

- Added `zimrate_publish_helper.py`, a safe publish/update verifier that:
  - fetches existing post payload before updates,
  - preserves required fields on `PUT /api/blog/{slug}`,
  - attaches `imageUrl` and `socialImageUrl`,
  - publishes drafts via `PATCH /publish`,
  - verifies live article HTTP status,
  - verifies public image HTTP status/content type,
  - verifies `og:image` and `twitter:image`,
  - validates X intent links via the existing X script,
  - optionally logs the published topic into `published_topics.json`.
- Added `format_scan_results.py`, a deterministic formatter for clean one-story-per-message Telegram scan delivery.
- Tightened scanner false-positive filters for police/crime stories that contain money words but are not economic coverage.
- Added currency synonym matching so `black market dollar` and `parallel market forex` are treated as related for novelty scoring.
- Changed top-story selection to prefer fresh stories before filling remaining slots with repeated coverage.
- Logged the ZiG parallel-market article in `published_topics.json` for novelty scoring.

## Remaining hardening

### 1. Draft creation helper

Create a script that validates and posts draft payloads from a saved markdown/JSON bundle:

- check word count 500 to 800,
- check no em/en dashes,
- check at least 2 internal links,
- check metadata length 120 to 160,
- POST as DRAFT,
- print preview URL only.

Suggested path: `/opt/forexzim/blog/scripts/zimrate_draft_helper.py`.

### 2. Cron delivery integration

Cron prompts should call `format_scan_results.py` and deliver each JSON block as its own Telegram message. The deterministic formatter now exists, but the cron prompts still need to be switched over when we next revise the scheduled jobs.

### 3. Image deployment bridge

Standardize non-template creative images and generated image-system images behind one deploy command:

- copy PNG to Spring static resources,
- rebuild JAR,
- restart `forexzim`,
- verify public image URL,
- return versioned URL.

### 4. End-to-end operator runbook

Create one command sequence for the human-approved path:

- `Write 1` -> brief + draft + preview,
- `publish` -> image attach/publish/verify/X options/topic log.

## Completion assessment

Roadmap item 5 is now **partially operational and actively hardened**.

- Scan and candidate handoff: working.
- Manual research/draft/preview: working.
- Image/social workflow: working, with helper coverage for verification.
- Publish/X workflow: working, with helper coverage for repeatability.
- Remaining work: deterministic draft helper, scan formatter, image deploy bridge, and final runbook.
