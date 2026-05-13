# Publishing Trigger Prompt

This handles the final step: pushing approved drafts live.

---

## PUBLISHING PROMPT (what the agent runs after Tino replies "Publish")

```
Tino approved draft: {{ slug }}

Execute Phase 6 of your workflow:

1. Send PATCH request to publish the draft:
   PATCH http://localhost:8090/api/blog/{{ slug }}/publish
   Headers: Authorization: Bearer 512d2584da6010b519f0183fe624d5f7

2. Confirm the API returned 200 OK. If it failed, report the error to Tino and do not log the publication.

3. On success, log to /opt/forexzim/blog/published_topics.json:
   {
     "cluster_id": "{{ cluster_id }}",
     "slug": "{{ slug }}",
     "primary_keyword": "{{ primary_keyword }}",
     "published_at": "{{ current_iso_timestamp }}"
   }

4. Send confirmation to Tino:
   "Published: {{ title }}
    Live at: https://zimrate.com/blog/{{ slug }}
    Logged to published_topics.json"

5. If multiple drafts are pending and Tino replied "Publish all", repeat for each draft in order.
```

---

## EDIT FLOW (when Tino replies "Edit: [instructions]")

```
Tino requested edits to draft: {{ slug }}

Edit instructions: {{ tino_instructions }}

1. Revise the draft according to instructions.
2. Re-run the HUMANIZER_RULES.md self-check.
3. Update the file at /opt/forexzim/blog/drafts/YYYY-MM-DD-slug.md
4. PATCH the API with the updated content (keep status as DRAFT):
   PATCH /api/blog/{{ slug }}
   Body: { "content": "..." }
5. Send revised preview to Tino with the same reply options.
```

---

## REJECT FLOW (when Tino replies "Reject")

```
Tino rejected draft: {{ slug }}

1. DELETE the draft from API:
   DELETE /api/blog/{{ slug }}
2. Move the local draft file to /opt/forexzim/blog/drafts/rejected/
3. Do NOT log to published_topics.json.
4. Confirm to Tino: "Rejected and archived: {{ title }}"
```
