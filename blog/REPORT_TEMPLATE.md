# ZimRate Daily Scan Report Template

This is the exact format the agent should send to Tino twice daily.
Keep it scannable. Tino should be able to reply with just story numbers.

---

## SAMPLE REPORT (what Tino receives)

```
ZIMRATE NEWS SCAN
12 May 2026, 10:00 CAT
Stories scanned: 47 | Clustered: 12 | Top 5 below

================================================================

[1] SCORE: 91/100
HEADLINE: RBZ tightens ZiG liquidity, banks face new reserve rules
SOURCES (3): Herald (08:00), NewsDay (09:30), ZimLive (10:15)
TOPIC: Monetary policy / ZiG
RECENCY: 2 hours
ANGLE FOR ZIMRATE: 
  "How the new RBZ liquidity rules could affect ZiG-USD rates 
   over the next 30 days. Tie to parallel market movements."
KEY FACTS:
  - Reserve ratio raised from 15% to 20%
  - Effective Monday 19 May
  - Estimated ZWL 800m to be locked up
NOVELTY: New story (not covered in last 14 days)

================================================================

[2] SCORE: 84/100
HEADLINE: ZIMRA revises presumptive tax brackets for SMEs
SOURCES (2): Zimbabwe Independent (07:00), The Standard (09:00)
TOPIC: Tax / ZIMRA
RECENCY: 3 hours
ANGLE FOR ZIMRATE:
  "What the new ZIMRA presumptive tax means for informal traders 
   and small businesses. Include a quick calculation example."
KEY FACTS:
  - New brackets effective 1 June 2026
  - Threshold raised from USD 1,000 to USD 1,500 monthly turnover
  - Penalty for non-compliance now USD 200 per month
NOVELTY: New story

================================================================

[3] SCORE: 78/100
HEADLINE: Diaspora remittances hit USD 1.6bn in Q1 2026
SOURCES (3): Herald (yesterday), New Zimbabwe (yesterday), Zim Mail (today 06:00)
TOPIC: Remittances / Diaspora
RECENCY: 4 hours (latest source)
ANGLE FOR ZIMRATE:
  "Why diaspora remittances keep growing despite ZiG instability. 
   Compare with same period 2025."
KEY FACTS:
  - 18% growth year-on-year
  - South Africa, UK, US top source countries
  - Mukuru and WorldRemit lead transfer volume
NOVELTY: Related topic covered 8 days ago (mild overlap)

================================================================

[4] SCORE: 72/100
HEADLINE: Fuel prices to rise on Wednesday, ZERA announces
SOURCES (2): Herald (09:00), NewsDay (09:45)
TOPIC: Fuel / Energy
RECENCY: 1 hour
ANGLE FOR ZIMRATE:
  "How fuel price hikes ripple through transport costs and 
   informal market prices. Tie to inflation outlook."
KEY FACTS:
  - Petrol up USD 0.04 to USD 1.62
  - Diesel up USD 0.03 to USD 1.58
  - Effective Wednesday 14 May
NOVELTY: New (last fuel price story 21 days ago)

================================================================

[5] SCORE: 65/100
HEADLINE: Tobacco auction floors open with stronger prices
SOURCES (1): NewsDay (yesterday 14:00)
TOPIC: Agriculture / Tobacco
RECENCY: 20 hours
ANGLE FOR ZIMRATE:
  "Why tobacco earnings matter for forex inflows and the ZiG. 
   Connect to broader USD supply story."
KEY FACTS:
  - Opening average USD 3.20/kg
  - Up from USD 2.85/kg last season
  - 200m kg expected this season
NOVELTY: New story
NOTE: Only 1 source, treat with caution. Flag claims as MEDIUM confidence.

================================================================

REPLY FORMAT:
Tino, reply with one of these:
  - "Write 1, 2, 4"           (draft these stories)
  - "Write all"               (draft top 5)
  - "Write 1, modify 3 angle: focus on women diaspora senders"
  - "Skip all, scan again at 17:00"

Drafts will appear in /opt/forexzim/blog/drafts/ within 15 minutes.
```

---

## REPORT DESIGN PRINCIPLES

1. **Score first** so Tino's eye lands on what matters.
2. **One-line angle** is mandatory. This is the most important field.
3. **Key facts in bullets** so Tino can verify the story is real and worth covering.
4. **Novelty flag** prevents accidental duplicate topics.
5. **Confidence note** when only 1 source backs the story.

---

## REPLY GRAMMAR (what the agent parses from Tino)

The agent should accept these patterns:

- `Write 1` -> draft story 1 only
- `Write 1, 3, 5` -> draft those three
- `Write all` -> draft top 5
- `Write 2, modify angle: [new angle text]` -> override the suggested angle
- `Skip all` -> no drafts this cycle
- `Rescan` -> trigger immediate re-scan
- `More options` -> show stories 6-10 from the original ranking

If Tino's reply does not match any pattern, the agent asks for clarification rather than guessing.

---

## DELIVERY CHANNEL

Send the report via whichever channel Tino prefers:
- Email (subject: "ZimRate Scan - 12 May 10:00")
- WhatsApp message
- Slack DM
- Telegram bot

Whatever channel, the report body stays in this exact format for consistency.

