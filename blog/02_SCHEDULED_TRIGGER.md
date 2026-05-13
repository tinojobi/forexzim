# Scheduled Scan Trigger Prompt

Use this prompt for the cron job or scheduler that runs at 10:00 and 17:00 CAT.

---

## TRIGGER PROMPT (paste this as the scheduled message)

```
Start a new scan cycle. Current time: {{ current_timestamp_CAT }}.

Execute Phases 1 through 4 of your workflow:

1. Pull RSS feeds from all 9 primary sources. Fall back to scraping where needed. Fall back to Google News if primary sources return nothing useful.

2. Filter to articles published in the last 24 hours only. Discard anything with an unverifiable date.

3. Cluster duplicate stories across outlets into single story groups.

4. Score every cluster using score_stories.py. Check published_topics.json to enforce the 14-day novelty rule.

5. Return the top 5 ranked stories formatted exactly per REPORT_TEMPLATE.md.

Important:
- Prefer stories published in the last 6 hours over older ones.
- If fewer than 5 stories pass quality filters, return however many qualify and note this in the report.
- If zero stories qualify, send a brief message saying "No qualifying stories this cycle, next scan at [next scheduled time]" and stop.

Send the report and wait for my reply.
```

---

## HOW TO SCHEDULE THIS

If your agent runs on a server with cron, add to crontab:

```
0 10 * * * /usr/bin/curl -X POST http://localhost:PORT/agent/run -d 'prompt=START_SCAN'
0 17 * * * /usr/bin/curl -X POST http://localhost:PORT/agent/run -d 'prompt=START_SCAN'
```

If your agent runs on a workflow tool like n8n, Make, Zapier, or Hermes:
- Set 2 schedule triggers (10:00 and 17:00 Africa/Harare timezone)
- Each trigger sends the prompt above to the agent
- Route the response to your preferred channel (WhatsApp, email, Slack)

---

## MANUAL TRIGGER (when you want an off-schedule scan)

Just send your agent:

```
Scan now.
```

Or for a specific focus:

```
Scan now, focus on ZiG and forex stories only.
```

```
Scan now, only stories from the last 6 hours.
```

