# X_POST_RULES.md
## ZimRate X (Twitter) Post Generation Rules

Apply these rules when drafting X posts to promote ZimRate articles.

---

## CORE GOAL

Get the reader to click the article link. That means hooking them with curiosity, surprise, or a personal stake in the first line. Not summarizing the article.

---

## POST FORMATS (pick one per article)

For each published article, generate 2 post variants in different styles. Tino picks one to use.

### Format 1: THE HOOK STAT
Lead with the single most surprising number from the article. One line. Then context. Then link.

Example:
```
Zimbabwe's gold reserves grew 250% in 18 months.

The RBZ now holds 4.48 tonnes of physical gold, up from 1.5t at ZiG launch.

What it means for the exchange rate: [link]
```

### Format 2: THE QUESTION
Open with a question your reader genuinely wants answered. Then promise the answer in the article.

Example:
```
If the RBZ holds 4.48 tonnes of gold, is the ZiG actually safe?

We broke down what the reserve number really means for anyone earning in local currency.

[link]
```

### Format 3: THE CONTRARIAN TAKE
State a counter-intuitive observation. Make the reader want to argue or agree.

Example:
```
A bigger gold reserve does not automatically mean a stronger ZiG.

Zimbabwe has been here before with reserve claims that later proved thin. The difference this time? Physical verification.

[link]
```

---

## STRICT RULES

### 0. IMAGE WORKFLOW
- Before presenting or posting X copy, verify the article exposes a non-default `og:image` / `twitter:image` when a hero/social image exists.
- The blog now supports `socialImageUrl` separately from `imageUrl`. Use `socialImageUrl` for X/social cards when the visible article image is removed.
- Best practice is still native media upload: upload the same image with `xurl media upload <path>` and attach it to the X post. Do not rely only on the link preview.

### 1. CHARACTER LIMIT
- Maximum 280 characters per post including the link.
- The link counts as 23 characters regardless of actual length.
- That leaves ~255 characters for your text.
- Aim for 200-250 characters. Leaves room for retweet comments.

### 2. STRUCTURE
- Line 1: The hook (one punchy sentence)
- Blank line
- Line 2-3: Context or payoff (1-2 short sentences)
- Blank line
- Line 4: Link to the article

### 3. BANNED OPENERS
Never start a post with:
- "New article:"
- "Just published:"
- "Check out our latest..."
- "We've written about..."
- "In this article..."
- "📰" or "🔔" or any "look at me" emoji
- The article title verbatim

### 4. NO JARGON DUMP
Use plain language. If a non-economist would not understand it instantly, rewrite.

WRONG: "The RBZ's monetary sovereignty framework leverages bullion accumulation to anchor ZiG forex stability."
RIGHT: "Zimbabwe's gold pile now backs every ZiG in your wallet."

### 5. NUMBERS BEAT WORDS
Specific numbers outperform vague descriptions. Always.

WRONG: "Zimbabwe's gold reserves have grown significantly."
RIGHT: "Zimbabwe's gold reserves jumped from 1.5t to 4.48t in 24 months."

### 6. ONE EMOJI MAXIMUM
- Allowed: 🇿🇼 (Zimbabwe flag), 📊 (data), 💵 (money), 🥇 (gold) when genuinely relevant
- Limit: One emoji per post, and only when it adds meaning
- Default: No emoji is fine

### 7. HASHTAGS
- Include relevant hashtags by default.
- Maximum 2 hashtags per post.
- Use only relevant, established tags: #Zimbabwe #ZiG #Forex #ZimEcon
- Place at the end, after the article link.
- Better to have 0 hashtags than forced or off-topic ones.

### 8. NO EM DASHES OR EN DASHES
Same rule as articles. Use commas, periods, or line breaks.

### 9. NO PROMOTIONAL FLUFF
Banned phrases:
- "Must-read"
- "You won't believe"
- "Game-changer"
- "Breaking news" (unless it actually broke)
- "Don't miss this"
- "Thread 👇"

### 10. THE LINK
- Always at the end on its own line
- Use the full article URL
- Do not say "click here" or "read more". The link speaks for itself.

### 11. MANUAL POST INTENT LINK
- Because X API posting is not currently enabled, include a tappable intent link for each final variant.
- Generate it with `/opt/forexzim/blog/scripts/x_intent_links.py` so URL encoding and character checks are consistent.
- The intent link opens X with the post prefilled. Tino reviews and taps Post manually.
- Intent links cannot attach native media. Make sure the article has a valid non-default `socialImageUrl` so the X link preview uses the article image.

---

## TONE GUIDELINES

ZimRate's voice on X:
- Grounded, not flashy
- Skeptical but not cynical  
- Helpful to ordinary Zimbabweans, not just economists
- Confident in its data, humble in its conclusions
- Local references welcome (mealie meal, school fees, parallel market, kombi fare)

---

## QUICK SELF-CHECK BEFORE FINALIZING

Before submitting the 2 variants, verify each:

1. Under 280 characters including the link?
2. Hook in the first line, not the title?
3. Plain language, no jargon dump?
4. At least one specific number?
5. Zero banned phrases or em dashes?
6. Maximum one emoji, maximum two hashtags?
7. Link at the end?
8. Would a non-economist Zimbabwean want to click?

---

## OUTPUT FORMAT

Present the 2 variants like this:

```
X POST OPTIONS for: [Article Title]
Article URL: [URL]

---
VARIANT 1 - HOOK STAT (240 chars)
[post text]

Tap to post:
[twitter intent URL]

---
VARIANT 2 - QUESTION (220 chars)  
[post text]

Tap to post:
[twitter intent URL]

---
Reply with "Use 1", "Use 2", or "Edit [number]: [instructions]"
```

---

## WHAT NOT TO DO

Examples of bad posts the agent should never produce:

BAD: "📰 New article on ZimRate! Mnangagwa visits RBZ and confirms gold reserves. Read more here 👉 [link] #Zimbabwe #News #Gold #Economy #Africa"
Why: Promotional opener, too many hashtags, emoji clutter, no specific hook.

BAD: "President Mnangagwa visited the Reserve Bank of Zimbabwe on Monday to inspect the country's gold reserves which now stand at 4.48 metric tonnes representing significant growth from earlier levels."
Why: No hook, reads like a news lede, no payoff, no question, makes the reader work for nothing.

BAD: "BREAKING: Zimbabwe gold reserves hit 4.48t! 🚨🇿🇼💰📊 You won't believe how much they've grown! Click to find out 👇 [link]"
Why: Fake urgency, emoji clutter, clickbait phrasing, no real information.

GOOD: "Zimbabwe's gold reserves grew 250% in 18 months. The RBZ now holds 4.48 tonnes, up from 1.5t at ZiG launch. We broke down what it actually means for the exchange rate: [link]"
Why: Specific hook, useful number, plain language, clear payoff for clicking.
