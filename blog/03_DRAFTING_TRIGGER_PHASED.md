# Drafting Trigger (Phased Workflow)

This replaces the simpler drafting prompt from earlier. Same agent, but it works in 3 distinct phases per article. No sub-agents needed.

The phased approach forces the agent to separate research from writing, which produces better drafts without extra token cost.

---

## PHASED DRAFTING PROMPT

```
Tino approved the following stories for drafting: {{ approved_story_numbers }}

For each approved story, work in 3 distinct phases. Do NOT skip ahead. Complete each phase fully before moving to the next.

================================================================
PHASE A: RESEARCH (no writing yet)
================================================================

Goal: Gather and verify facts. Do not write any article prose.

Steps:
1. Open every source URL from the story cluster.
2. Read the full articles, not just headlines.
3. Extract every factual claim into a structured research brief.

Output a research brief in this exact format:

---
STORY: {{ headline }}
CLUSTER_ID: {{ cluster_id }}

KEY FACTS (with confidence tags):
- [HIGH] Fact 1 (Source: Herald + NewsDay + ZimLive)
- [HIGH] Fact 2 (Source: Herald + NewsDay)
- [MEDIUM] Fact 3 (Source: Herald only)
- [LOW] Fact 4 (Source: iHarare only, unverified)

KEY QUOTES (attributed):
- "Quote text" - Speaker name, role (Source outlet, date)

KEY NUMBERS (with sources):
- USD 1.6bn remittances Q1 2026 (Source: RBZ via Herald)
- 18% YoY growth (Source: RBZ via Herald)

CONTEXT NEEDED FOR READER:
- Background fact 1 (why this matters)
- Background fact 2 (how it connects to ZiG/forex)

ANGLE FROM TINO: {{ approved_angle }}

POTENTIAL INTERNAL LINKS:
- zimrate.com/converter (rate calculation reference)
- zimrate.com/blog/[related-slug] (previous coverage)

PRIMARY KEYWORD: {{ keyword }}
TARGET WORD COUNT: 500-800
---

Self-check before moving to Phase B:
- Does every fact have a confidence tag?
- Are LOW-confidence facts flagged for dropping or quoting only?
- Are at least 3 HIGH-confidence facts available?

If fewer than 3 HIGH-confidence facts, STOP. Report to Tino:
"Insufficient verification for story {{ headline }}. Only X HIGH-confidence facts available. Recommend skipping or waiting for more sources."

================================================================
PHASE B: WRITE (using only the brief from Phase A)
================================================================

Goal: Write a 500-800 word article using ONLY facts from the brief.

Rules:
1. Use ONLY [HIGH] and [MEDIUM] confidence facts in the body.
2. [LOW] facts may appear only as direct attributed quotes.
3. Follow HUMANIZER_RULES.md strictly.
4. HTML format only. Allowed tags: <p>, <h2>, <h3>, <a href>, <strong>, <em>, <ul>, <li>.
5. Inline source citations for every statistic: "(Herald, May 2026)".
6. Include 2-3 internal links from the brief's POTENTIAL INTERNAL LINKS section.
7. End with the disclaimer:
   <p><em>This article is for informational purposes only and does not constitute financial advice.</em></p>

Structure suggestion (adapt as the story needs):
- Lead paragraph: the news, who it affects, when it takes effect
- 2-3 body sections with <h2> headers
- Context section: why this matters
- Closing: what to watch next
- Disclaimer

================================================================
PHASE C: SELF-CHECK AND METADATA
================================================================

Goal: Verify the draft passes all rules, then generate metadata.

Step 1: Run the HUMANIZER_RULES.md self-check. Answer YES to all 11 items.

If any check fails, REWRITE the offending section. Do not submit a failing draft.

Step 2: Generate metadata:
- title: max 70 chars, compelling, includes primary keyword
- slug: lowercase, hyphenated, max 60 chars, keyword-front-loaded
- meta_description: 120-160 chars, includes primary keyword + "ZimRate"
- read_time_minutes: word_count / 200, rounded up
- primary_keyword: from the brief
- cluster_id: from the brief

Step 3: Save the draft to /opt/forexzim/blog/drafts/YYYY-MM-DD-slug.md

Step 4: POST to API with status DRAFT.

Step 5: Send preview to Tino with:
- Title
- Word count
- Preview link
- First paragraph as teaser
- Reply options: "Publish" / "Edit: [instructions]" / "Reject"

================================================================
ORDER OF OPERATIONS FOR MULTIPLE STORIES
================================================================

If Tino approved 3 stories, complete them in this order:

1. Research brief for story 1 -> Send brief preview to Tino (optional)
2. Draft article for story 1 -> Send draft preview to Tino
3. Research brief for story 2 -> Send brief preview (optional)
4. Draft article for story 2 -> Send draft preview to Tino
5. Continue...

This lets Tino review each piece sequentially. He can edit or reject any draft before you move to the next.

If Tino prefers batch review, he will say "batch all" and you complete all research + writing, then send all drafts at once.
```

---

## WHY THIS WORKS WITHOUT SUB-AGENTS

The agent's brain switches modes between phases:

Phase A: "I am a researcher. Accuracy is everything."
Phase B: "I am a writer. Voice and flow are everything. I cannot invent facts because I only have the brief."
Phase C: "I am an editor. I check rules and produce metadata."

Same context window, same cost, but the structured separation forces better output. Most failure modes in AI writing come from trying to do everything at once.

---

## OPTIONAL ENHANCEMENT: SAVE THE BRIEFS

You may want to keep research briefs as a paper trail. Add this to Phase A:

Save the brief to /opt/forexzim/blog/briefs/YYYY-MM-DD-slug-brief.md

Useful for:
- Auditing claims later if a fact is disputed
- Re-using research if you want a follow-up article on the same topic
- Training future versions of the prompt by seeing what good briefs look like
