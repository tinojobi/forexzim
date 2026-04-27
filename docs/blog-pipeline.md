# ZimRate Blog Pipeline — Full Process Document

## Overview

The ZimRate blog publishes Zimbabwe economic news articles on zimrate.com. The pipeline covers everything from topic discovery to live publication. Articles are written by Nova (research agent), reviewed by Tony (orchestrator), approved by Tino (owner), and published via the blog API.

**Stack:** Spring Boot backend, PostgreSQL database, Flyway migrations
**API base:** `http://localhost:8090/api/blog`
**Live site:** https://zimrate.com/blog

---

## 1. Topic Discovery

### Sources (check in order)

| Source | URL | Notes |
|--------|-----|-------|
| Herald | herald.co.zw / heraldonline.co.zw | State media, RBZ announcements |
| NewsDay | newsday.co.zw | Independent, economic coverage |
| ZimLive | zimlive.com | Investigative journalism |
| Zimbabwe Independent | zimbabweindependent.com | Business/finance focus |
| The Source | thesource.co.zw | Business wire service |
| iHarare | iharare.com | General news, trending stories |
| New Zimbabwe | newzimbabwe.com | Diaspora-focused |
| Southern Eye | southerneye.co.zw | Regional (Bulawayo) |
| Zimbabwe Mail | zimbabwemail.co.zw | General news |
| The Standard | thestandard.co.zw | Sunday paper, in-depth |
| RBZ | rbz.co.zw | Official press releases |
| ZimStats | zimstats.co.zw | Official statistics |
| Google News | news.google.com | Fallback if primary sources lack the story |

### Trigger Schedule

- **Automated scan:** 10:00 AM and 5:00 PM CAT daily
- **Weekly post:** Monday 8:00 AM CAT (cron trigger)
- **Breaking news:** Tino can request anytime

### Duplicate Check — Do This First

Before researching any topic, Nova must call the list endpoint to see what is already published or in draft:

```bash
curl http://localhost:8090/api/blog
```

Returns a summary list (slug, title, status, publishedAt) for all posts. If the topic is already covered and the existing article is less than 4 weeks old, skip it or find a meaningfully different angle. Do not write a near-duplicate.

### Topic Selection Criteria

Good topics:
- ZiG policy changes, exchange rate movements
- Inflation data, CPI releases
- Forex regulations, RBZ monetary policy
- Mining sector news (gold, platinum, lithium)
- Diaspora remittances, cross-border payments
- ZIMRA tax updates, compliance deadlines
- Agricultural season reports
- Budget analysis, fiscal policy
- Practical banking/finance guides

Skip topics:
- Rumours or unconfirmed reports
- Political opinion pieces
- Stories with fewer than 3 independent sources
- Topics covered recently (check the list endpoint above)

---

## 2. Research Protocol

Nova follows the **Deep Research** methodology:

```
Scope → Search → Evaluate → Deepen → Synthesize → Document → Deliver
```

### Step 1: Scope

Before searching, clarify:
- What exactly needs answering?
- What depth is required? (Standard for blog posts)
- What is the decision this enables? (Article for ZimRate audience)
- Time budget? (30-60 minutes max for a blog post)

### Step 2: Search

- Start broad, then narrow
- Multiple search engines/sources
- Follow citation trails
- Check primary sources when secondary sources cite them
- Look for contradicting viewpoints
- Track every source with links

### Step 3: Evaluate

For each source, check:
- **Authority:** Who wrote this? What credentials?
- **Recency:** When? Still valid?
- **Evidence:** Claims backed by data?
- **Bias:** Any agenda or conflict?
- **Corroboration:** Do others confirm this?

Flag low-credibility sources. Weight findings accordingly.

### Step 4: Deepen

Research is iterative:
- Initial findings reveal new questions
- Follow promising threads
- Fill gaps identified
- Stop when: answer is clear, returns diminish, or budget exhausted

### Step 5: Synthesize

Merge findings:
- Reconcile contradictions explicitly
- Weight by source quality
- Note confidence levels
- Identify remaining unknowns

### Minimum Source Requirements

- **Every factual claim:** 3 independent sources minimum
- **Statistics/data:** Must cite original source (RBZ, ZimStats, IMF)
- **Quotes:** Must be directly attributable with source link
- **If unverifiable:** Omit from article entirely

---

## 3. Article Writing

### Structure

```
Title: [Clear, specific, 50-80 characters]
Excerpt: [One sentence summary, under 500 chars]
Meta description: [120-160 chars, includes main keyword + "ZimRate"]

Body:
  Opening paragraph (context + why it matters)
  Background section (what led to this)
  Key details (numbers, dates, specifics)
  Impact on ordinary Zimbabweans (practical, not theoretical)
  What's next (if applicable)
  Internal links paragraph (homepage, converter, history)
  Disclaimer footer
```

### Tone and Style

- Informative, plain English
- Like explaining to a friend over coffee
- Specific over vague
- Data-driven, not opinion-driven
- Acknowledge uncertainty when it exists
- Vary sentence length and rhythm
- No AI writing patterns (see Humanizer Checklist below)

### Word Count

- Target: 500-800 words
- Minimum: 400 words
- Maximum: 1000 words
- Read time: calculated from word count (200 words/minute)

---

## 4. Humanizer Checklist

Before saving any draft, run through this checklist. Every item must pass.

### BANNED — Remove immediately

| Pattern | Example | Fix |
|---------|---------|-----|
| Em dashes (—) or en dashes (–) | "goods — from medicine to electronics" | "goods, from medicine to electronics" |
| "serves as" / "stands as" | "serves as a testament" | "is" or rewrite |
| Rule of three | "innovation, inspiration, and insights" | Pick the best one or two |
| AI vocabulary | crucial, pivotal, delve, tapestry, vibrant, landscape | Replace with specific words |
| Vague attributions | "Experts argue..." | "Economist [Name] at [Institution] said..." |
| Promotional language | "groundbreaking", "must-visit", "stunning" | Neutral descriptors |
| Negative parallelisms | "Not just X, it's Y" | Just state Y |
| "-ing" analyses | "highlighting the significance" | Remove or rewrite |
| Elegant variation | Using 4 synonyms for the same thing | Repeat the word, it's fine |
| Generic conclusions | "The future looks bright" | Specific next step or nothing |
| Sycophantic tone | "Great question! Absolutely!" | Just answer |
| Curly quotes (" ") | "the project is on track" | Straight quotes: "the project is on track" |
| Emojis in headings | Launch Phase | Remove emoji |
| Filler phrases | "In order to achieve this goal" | "To do this" |
| Excessive hedging | "It could potentially possibly be argued" | "It may" |
| Knowledge disclaimers | "As of my last update..." | Just state the fact or omit |

> **Note:** Em dashes and en dashes are now enforced server-side. The API will reject any content containing — or – with a 400 error.

### REQUIRED — Must be present

| Requirement | Check |
|-------------|-------|
| Inline source citations | Every stat has a source link inline |
| No misleading timeframes | Exact timelines verified |
| Internal links (2-3 minimum) | Homepage, converter, history, or related articles |
| Disclaimer footer | "This article is for informational purposes only..." |
| Specific details | Names, numbers, dates, not vague claims |
| Varied sentence structure | Mix short and long sentences |
| Human voice | Sounds like a person wrote it, not a template |

---

## 5. Internal Links (Mandatory)

Every article must include at least 2 internal links. The API will reject content with fewer than 2. Use these:

```html
<!-- Homepage (rates dashboard) -->
<a href="/">ZimRate homepage</a>

<!-- Currency converter -->
<a href="/convert">currency converter</a>
<a href="/convert/100-usd-to-zig">USD to ZiG converter</a>

<!-- Exchange rate history -->
<a href="/history">historical exchange rate trends</a>

<!-- Related blog posts (if relevant) -->
<a href="/blog/[slug]">related article title</a>
```

Placement: Add in a natural paragraph near the end, before the disclaimer. Example:

> For the latest exchange rates, visit the <a href="/">ZimRate homepage</a> or use our <a href="/convert">currency converter</a>. Track <a href="/history">historical exchange rate trends</a> to stay informed.

---

## 6. API Reference

**ALWAYS use the API. NEVER create Flyway migration files for blog posts.**

### Base URL

```
http://localhost:8090/api/blog
```

---

### GET /api/blog — List all posts

Returns a summary of all posts (no content body). Use this before writing to check existing coverage. No auth required.

```bash
# All posts
curl http://localhost:8090/api/blog

# Filter by status
curl "http://localhost:8090/api/blog?status=DRAFT"
curl "http://localhost:8090/api/blog?status=PUBLISHED"

# Keyword search (searches title, excerpt, metaDescription)
curl "http://localhost:8090/api/blog?q=fuel+prices"
curl "http://localhost:8090/api/blog?status=PUBLISHED&q=ZiG"
```

**Response fields:** `id`, `slug`, `title`, `status`, `excerpt`, `metaDescription`, `wordCount`, `readTimeMinutes`, `publishedAt`, `updatedAt`

---

### GET /api/blog/{slug} — Get a single post

Returns the full post including content. No auth required.

```bash
curl http://localhost:8090/api/blog/what-is-zimbabwe-gold-zig
```

**Response codes:**

| Code | Meaning |
|------|---------|
| 200 | Post returned |
| 404 | No post with that slug |

---

### POST /api/blog — Create a new post

**Requires `X-Admin-Token` header.**

**Default status is DRAFT.** Must explicitly pass `"status": "PUBLISHED"` to go live immediately.

#### Required Fields

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| title | string | Required, max 255 chars | Clear, specific headline |
| excerpt | string | Required, max 500 chars | One-sentence summary |
| content | string | Required, HTML format | Must have 2+ internal links, no em/en dashes |
| metaDescription | string | Required, 120-160 chars | SEO description with keyword |

#### Optional Fields

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| slug | string | Auto-generated from title | Must be unique |
| status | string | `"DRAFT"` | `"DRAFT"` or `"PUBLISHED"` |
| readTimeMinutes | integer | Auto-calculated | Estimated at 200 words/minute |
| faqItems | array | null | FAQ structured data (see below) |

#### Example — Create a draft

```bash
curl -X POST http://localhost:8090/api/blog \
  -H "Content-Type: application/json" \
  -H "X-Admin-Token: YOUR_ADMIN_TOKEN" \
  -d '{
    "title": "RBZ Holds Rate at 20%",
    "slug": "rbz-holds-rate-at-20-percent",
    "excerpt": "The Reserve Bank of Zimbabwe kept its benchmark rate unchanged at 20% citing stable inflation.",
    "content": "<p>The Reserve Bank of Zimbabwe kept its benchmark rate unchanged at 20% in its latest monetary policy committee meeting.</p><p>Governor John Mushayavanhu said the decision reflected confidence in ZiG stability following three consecutive months of single-digit inflation.</p><p>For the latest exchange rates, visit the <a href=\"/\">ZimRate homepage</a> or use our <a href=\"/convert\">currency converter</a>. Track <a href=\"/history\">historical exchange rate trends</a> to stay informed.</p><p><em>This article is for informational purposes only and does not constitute financial advice.</em></p>",
    "metaDescription": "RBZ keeps benchmark interest rate at 20% as Zimbabwe inflation holds steady. ZimRate breaks down what it means for your money.",
    "status": "DRAFT"
  }'
```

#### Response codes

| Code | Meaning |
|------|---------|
| 201 | Created successfully |
| 400 | Validation failed (check error message) |
| 409 | Slug already exists |

---

### PUT /api/blog/{slug} — Update an existing post

**Requires `X-Admin-Token` header.**

Replaces all editable fields. Slug is immutable. Same field rules and validations as POST apply.

```bash
curl -X PUT http://localhost:8090/api/blog/rbz-holds-rate-at-20-percent \
  -H "Content-Type: application/json" \
  -H "X-Admin-Token: YOUR_ADMIN_TOKEN" \
  -d '{
    "title": "RBZ Holds Rate at 20% — Updated",
    "excerpt": "Updated excerpt here.",
    "content": "<p>Updated content...</p><p>Visit the <a href=\"/\">ZimRate homepage</a> or our <a href=\"/convert\">currency converter</a>.</p><p><em>This article is for informational purposes only.</em></p>",
    "metaDescription": "RBZ keeps benchmark rate at 20% as Zimbabwe inflation holds steady. What it means for your money.",
    "status": "DRAFT"
  }'
```

**Response codes:**

| Code | Meaning |
|------|---------|
| 200 | Updated successfully |
| 400 | Validation failed |
| 404 | No post with that slug |

---

### PATCH /api/blog/{slug}/publish — Publish a draft

**Requires `X-Admin-Token` header.**

Transitions a DRAFT to PUBLISHED. Sets `published_at` if not already set. Safe to call on an already-published post (returns 200 with no changes).

```bash
curl -X PATCH http://localhost:8090/api/blog/rbz-holds-rate-at-20-percent/publish \
  -H "X-Admin-Token: YOUR_ADMIN_TOKEN"
```

**Response codes:**

| Code | Meaning |
|------|---------|
| 200 | Published (or already was) |
| 403 | Missing or invalid token |
| 404 | No post with that slug |

---

### PATCH /api/blog/{slug}/unpublish — Retract a published post

**Requires `X-Admin-Token` header.**

Moves a PUBLISHED post back to DRAFT without deleting it. Use when a post has an error or needs updating before it is visible again.

```bash
curl -X PATCH http://localhost:8090/api/blog/rbz-holds-rate-at-20-percent/unpublish \
  -H "X-Admin-Token: YOUR_ADMIN_TOKEN"
```

**Response codes:**

| Code | Meaning |
|------|---------|
| 200 | Unpublished (or already was a draft) |
| 403 | Missing or invalid token |
| 404 | No post with that slug |

---

### Authentication

All write endpoints (POST, PUT, PATCH) require the `X-Admin-Token` header matching the `ADMIN_TOKEN` environment variable on the server. GET endpoints are public.

A missing or wrong token returns `403 Forbidden`.

### API Validation Rules

The API enforces all of these server-side and returns a 400 with a descriptive error message if any fail:

1. Title required (max 255 chars)
2. Excerpt required (max 500 chars)
3. Content required (HTML format)
4. Meta description required (120-160 chars)
5. Content must have at least 2 internal links (`href` starting with `/` or `https://zimrate.com`)
6. Content must not contain em dashes (—) or en dashes (–)
7. Script tags are automatically stripped
8. Slug must be unique (409 Conflict if duplicate)
9. Status defaults to DRAFT if not specified

---

## 7. Publishing Workflow

```
1. Tino requests article (or scheduled trigger fires)
2. Tony calls GET /api/blog to check existing coverage
3. Tony spawns Nova with topic + full instructions
4. Nova researches (3+ sources per claim)
5. Nova writes article in HTML format
6. Nova runs humanizer checklist
7. Nova saves draft to /opt/forexzim/blog/drafts/YYYY-MM-DD-slug.md
8. Nova calls POST /api/blog with status "DRAFT"
9. Nova sends the returned slug to Tony for review
10. Tony calls GET /api/blog/{slug} to review the saved draft
11. Tony checks: sources, SEO, humanization, internal links
12. Tony sends draft to Tino for approval
13. Tino approves (or requests changes via PUT)
14. Tony calls PATCH /api/blog/{slug}/publish
15. Article is live immediately — no restart needed
```

### Making Changes After Saving

If Tino requests edits after step 8, use PUT:

```bash
curl -X PUT http://localhost:8090/api/blog/{slug} \
  -H "Content-Type: application/json" \
  -d '{ ...full updated fields... }'
```

Never edit blog posts by writing Flyway migrations or raw SQL.

### Status Flow

```
POST (DRAFT) → review → PATCH /{slug}/publish → PUBLISHED
```

---

## 8. Content Format Rules

Content must be **HTML**, not markdown.

```html
<p>Paragraph text</p>
<h2>Section heading</h2>
<h3>Sub-section heading</h3>
<strong>Bold text</strong>
<a href="/convert">link text</a>
<blockquote>Quoted text</blockquote>
```

Do NOT use markdown (`#`, `##`, `**`, `*`). The API stores content as-is and the frontend renders it directly.

---

## 9. Quality Checklist

Before calling PATCH to publish, verify:

- [ ] All claims have 3+ independent sources
- [ ] Every statistic has an inline source citation
- [ ] No em dashes (—) or en dashes (–) anywhere (API will also reject these)
- [ ] No AI writing patterns (check humanizer list)
- [ ] At least 2 internal links to zimrate.com pages
- [ ] Meta description is 120-160 characters
- [ ] Title is clear and specific (50-80 chars)
- [ ] Excerpt summarizes the article in one sentence (under 500 chars)
- [ ] Content is HTML (not markdown)
- [ ] Disclaimer footer is present
- [ ] Read time is accurate for word count
- [ ] No misleading timeframes
- [ ] Tone is informative, not promotional
- [ ] Article sounds like a human wrote it
- [ ] Duplicate check done (GET /api/blog checked before writing)

---

## 10. Common Mistakes to Avoid

| Mistake | Fix |
|---------|-----|
| Using markdown in content | Use HTML (`<h2>`, `<strong>`, `<p>`) |
| Creating Flyway migrations for blog posts | Use the API |
| Restarting the service after adding a post | API updates are live immediately |
| Using em dashes or en dashes | Use commas, colons, or parentheses. API enforces this |
| Vague attributions ("Experts say...") | Name the expert and institution |
| Fewer than 3 sources for a claim | Find more sources or omit the claim |
| Fewer than 2 internal links | API will reject — add at least 2 links |
| Meta description under 120 chars | Write a fuller description (120-160 chars) |
| Publishing without Tino's approval | Always get approval first, then call PATCH /publish |
| Forgetting the disclaimer | Every article ends with the disclaimer |
| Editing via raw SQL | Use PUT /api/blog/{slug} instead |
| Writing a duplicate topic | Call GET /api/blog before starting research |
| Not saving as DRAFT first | POST always as DRAFT, publish only after approval |

---

## 11. File Locations

| Item | Path |
|------|------|
| Article drafts | /opt/forexzim/blog/drafts/ |
| Blog API controller | src/main/java/com/forexzim/controller/BlogApiController.java |
| Request DTO | src/main/java/com/forexzim/dto/BlogPostRequest.java |
| Blog repository | src/main/java/com/forexzim/repository/BlogRepository.java |
| Database | PostgreSQL, database: forexzim, table: blog_posts |
| Research skill | /root/.openclaw/workspace/skills/in-depth-research/SKILL.md |
| Humanizer skill | /root/.openclaw/workspace/skills/humanizer/SKILL.md |

---

## 12. Quick Reference

| Item | Value |
|------|-------|
| Research agent | Nova |
| Review agent | Tony (orchestrator) |
| Approver | Tino (owner) |
| List posts | `GET /api/blog` (public) |
| Search posts | `GET /api/blog?q=keyword` (public) |
| Get post | `GET /api/blog/{slug}` (public) |
| Create post | `POST /api/blog` (auth required) |
| Update post | `PUT /api/blog/{slug}` (auth required) |
| Publish draft | `PATCH /api/blog/{slug}/publish` (auth required) |
| Unpublish post | `PATCH /api/blog/{slug}/unpublish` (auth required) |
| Content format | HTML (not markdown) |
| Default status | DRAFT |
| Min sources per claim | 3 |
| Min internal links | 2 (API enforced) |
| Meta description | 120-160 chars (API enforced) |
| Em/en dashes | Banned (API enforced) |
| Never | Create migrations for blog posts, use raw SQL to edit, publish without Tino's approval |
