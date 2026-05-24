# ZimRate Editorial Article Image System

This directory contains the reusable image workflow for ZimRate article hero images.

## Goal

Generate premium magazine-cover editorial graphics for ZimRate articles without every post looking identical.

The system enforces brand consistency while rotating composition families:

- shared ZimRate palette
- shared editorial typography
- shared masthead/wordmark treatment
- shared no-card/no-dashboard rules
- varied hero placement, quote placement, rule structure and stat treatment

## Main script

```bash
/usr/bin/python3 /opt/forexzim/blog/image_system/zimrate_editorial_image.py \
  --spec /opt/forexzim/blog/image_system/examples/gdp-growth-forecast.json \
  --out /opt/forexzim/blog/image_system/output/gdp-growth-forecast-editorial.png \
  --report /opt/forexzim/blog/image_system/output/gdp-growth-forecast-qa.json
```

Use `/usr/bin/python3`, not the Hermes venv Python. The system Python has matplotlib available.

## Layout rotation

Set `image.layout` to one of:

- `auto` (default): deterministic rotation based on slug and article type
- `left_monument`: huge number left, copy right, footer stat band
- `split_masthead`: vertical split, number left, analysis right
- `editorial_poster`: large editorial word plus smaller hero stat
- `big_quote`: pull quote dominates, stat becomes supporting mark
- `diagonal`: asymmetric angled editorial field, good for tension/risk
- `ledger`: finance-ledger composition for tax, debt, budget or company finance

Set `image.article_type` to steer auto layout selection:

- `macro_forecast`
- `currency`
- `rate_update`
- `comparison`
- `tax`
- `company_finance`
- `policy_decision`
- `risk_warning`

The generator chooses from a layout pool for each article type, using the article slug as a deterministic seed. Same article means same default layout. Different articles rotate automatically.

## Spec shape

```json
{
  "article": {
    "title": "Article title",
    "slug": "article-slug"
  },
  "image": {
    "layout": "auto",
    "article_type": "macro_forecast",
    "masthead": "Z I M R A T E   I N S I G H T S",
    "issue": "MAY 2026 · ISSUE 01",
    "eyebrow": "Zimbabwe · 2026 outlook",
    "hero_number": "10",
    "hero_unit": "%",
    "hero_word": "STABILITY",
    "headline_context": "Upper growth forecast after the IMF programme.",
    "pull_quote": "Growth is easy. Stability is harder."
  },
  "stats": [
    {"label": "Min forecast", "value": "8.5%"},
    {"label": "Earlier view", "value": "6.6%"},
    {"label": "Inflation", "value": "4.1%"},
    {"label": "Current account", "value": "3.8% GDP"}
  ]
}
```

`hero_word` is mainly used by `editorial_poster`. If omitted, the generator derives a short word from the eyebrow.

## Output files

The script produces:

- PNG image
- JSON QA report with:
  - pass/fail
  - selected layout
  - layout selection reason
  - detected text overlaps
  - canvas clipping
  - hero number/unit spacing
  - decorative rule collisions
  - text bounding boxes for debugging

## Testing all layouts

Examples exist for each layout:

```bash
for layout in left_monument split_masthead editorial_poster big_quote diagonal ledger; do
  /usr/bin/python3 /opt/forexzim/blog/image_system/zimrate_editorial_image.py \
    --spec /opt/forexzim/blog/image_system/examples/${layout}.json \
    --out /opt/forexzim/blog/image_system/output/${layout}.png \
    --report /opt/forexzim/blog/image_system/output/${layout}-qa.json
done
```

The current sample set passes automated QA for all six layouts.

## Deploying an image

To copy the generated PNG into Spring static resources:

```bash
/usr/bin/python3 /opt/forexzim/blog/image_system/zimrate_editorial_image.py \
  --spec SPEC.json \
  --out OUT.png \
  --report QA.json \
  --deploy-name public-file-name.png
```

To also rebuild the app and restart the service:

```bash
/usr/bin/python3 /opt/forexzim/blog/image_system/zimrate_editorial_image.py \
  --spec SPEC.json \
  --out OUT.png \
  --report QA.json \
  --deploy-name public-file-name.png \
  --rebuild
```

After deployment, use cache busting when updating the article image URL:

```text
https://zimrate.com/images/public-file-name.png?v=1
```

Increment `v=` whenever replacing an image, because Cloudflare can cache older assets or 404s.

## Mandatory workflow

1. Create a JSON spec from the article facts.
2. Use `layout: auto` unless there is a strong editorial reason to force a layout.
3. Run the generator.
4. If automated QA fails, fix the spec or template before deploying.
5. Open the rendered PNG and visually inspect it.
6. Only deploy after both automated QA and visual QA pass.
7. Attach it to the draft using full `PUT /api/blog/{slug}` with the existing article payload plus `imageUrl` and `socialImageUrl`.
8. If the visible article image should be removed, clear only `imageUrl` and keep `socialImageUrl` so X/social previews still use the hero image.
9. Verify the public image URL returns HTTP 200 and the article HTML exposes it as `og:image` and `twitter:image`.
10. Send Tino the preview link and the image, not raw implementation details.

## Human visual QA checklist

Even with automated QA, inspect the actual PNG. Matplotlib font metrics can over-report or under-report in edge cases.

Check:

- Hero number dominates but is not clipped.
- Hero unit, especially `%`, has visible cream space from the number.
- Context text is readable and not touching decorative rules.
- Pull quote is separated from the context block.
- Footer labels sit above footer numbers with no overlap.
- Long values such as `3.8% GDP` fit.
- Wordmark is visible and not colliding.
- The image feels like a magazine cover, not a dashboard.
- The selected layout is not too similar to the last few published images.

## Design constraints encoded in the template

- 2400 x 1200 wide canvas.
- Warm cream/pale gold background.
- Deep forest/charcoal primary text.
- Muted amber accent.
- Serif hero typography.
- No cards, rounded boxes, 2x2 grids, shadows, icons, blue/purple gradients, or neon.
- Subtle paper grain.
