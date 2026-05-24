# ZimRate Editorial Image Style Guide

Use this style for ZimRate article hero images unless Tino requests a different direction.

## Visual target

Premium editorial magazine cover, not infographic dashboard.

References:

- The Economist
- Bloomberg Businessweek
- Monocle
- Stripe Press
- Pitch

The image should feel restrained, confident and art-directed.

## Consistency without sameness

The system should not produce the exact same composition for every article. Keep the brand language fixed, but rotate the layout.

Fixed brand rules:

- warm cream/pale-gold background
- forest green or charcoal hero typography
- muted amber accents
- ZimRate masthead and wordmark
- serif hero number or hero word
- small uppercase labels
- subtle paper grain
- no dashboard cards

Variable composition rules:

- hero number can sit left, lower-left, top-left or as a smaller supporting mark
- pull quote can dominate or support
- rules can be horizontal, vertical or asymmetric
- footer stats can be full-width, compact or ledger-style
- some articles can use a hero word instead of only a hero number

## Layout families

The generator supports these layouts:

1. `left_monument`  
   Huge number left, supporting copy right, footer stat band. Best for macro numbers and simple headline figures.

2. `split_masthead`  
   Vertical split with number on one side and analysis on the other. Best for comparisons, rates and before/after stories.

3. `editorial_poster`  
   Large editorial word plus a smaller hero stat. Best for policy, stability, debt, reform and institutional stories.

4. `big_quote`  
   Pull quote dominates, hero stat acts as supporting mark. Best when the article has a strong thesis or question.

5. `diagonal`  
   Asymmetric angled visual field. Best for tension, risk, uncertainty or “good number, but watch the catch” stories.

6. `ledger`  
   Financial ledger style. Best for tax, debt, company finance, budget and ZIMRA stories.

Use `layout: auto` by default. The generator rotates layouts deterministically by slug and article type.

## Palette

Use only these color families:

- Background: warm cream or pale gold, `#f4e4c1`, `#f7eed3`, `#faf4e3`
- Primary text and hero number: deep charcoal `#1a1a1a` or forest green `#0d3d2e`
- Accent: muted amber/gold, `#8a5a1a`, `#b07a1f`, `#c8941f`
- Secondary text: warm taupe, `#5a3a0a`, `#6e4a1a`

Never use:

- pure white backgrounds
- neon
- candy pastels
- blue or purple gradients
- more than three color families
- glossy effects
- 3D
- glassmorphism

## Layout rules

- One element must dominate visually: either a hero number, hero word or pull quote.
- Place the dominant element left, left-center or asymmetrically. Avoid dead center.
- Body copy and supporting text float around the hero number.
- Do not place body copy inside cards.
- No rounded stat cards.
- No 2x2 grids.
- No equal-sized boxes.
- Thin horizontal or vertical rules separate editorial sections.
- Footer stats sit in a horizontal band or ledger zone with whitespace between them.
- Allow asymmetric whitespace.

## Typography

- Hero number/word: serif, large, tight, confident.
- Pull quote: italic serif.
- Labels, masthead, wordmark: sans serif, uppercase, letter-spaced feel.
- Footer stat values: serif numbers.

## Required elements

- Top masthead row: `ZimRate Insights` or `ZimRate Quarterly`.
- Issue/date on top right.
- Amber uppercase eyebrow.
- Hero number with separate accent unit where applicable.
- Short context line, max 25 words.
- Italic pull quote, max 18 words.
- 3 to 4 supporting stats.
- Bottom-right `zimrate.com` wordmark.
- Subtle paper grain, 2 to 4 percent opacity.

## Failure conditions

Regenerate if any of these appear:

- `%` overlaps the hero number.
- Footer labels collide with footer values.
- Long footer values such as `3.8% GDP` overflow their zone.
- Decorative rules cross copy or quote text.
- Copy is clipped by image edges.
- The design looks like a Canva dashboard.
- All stats have equal visual weight unless the layout intentionally uses a ledger treatment.
- Generic icons appear next to numbers.
- Any bright color outside the palette appears.
- The current article image looks too similar to the last few published images.

## Automation rule

Use `/opt/forexzim/blog/image_system/zimrate_editorial_image.py` for production article images. It produces a PNG and JSON QA report. Automated QA is necessary but not sufficient. Always visually inspect the rendered PNG before deployment.
