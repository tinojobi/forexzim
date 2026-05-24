#!/usr/bin/env python3
"""Format ZimRate scan results for clean Telegram delivery.

Reads /opt/forexzim/blog/scan_results.json and emits JSON blocks:
- header
- one message per story
- footer

Cron agents can use this deterministic output instead of asking the LLM to invent
formatting every run.
"""
from __future__ import annotations

import argparse
import json
from datetime import datetime
from pathlib import Path
from typing import Any

DEFAULT_SCAN_RESULTS = Path("/opt/forexzim/blog/scan_results.json")


def parse_dt(value: str | None) -> str:
    if not value:
        return "unknown time"
    try:
        dt = datetime.fromisoformat(str(value))
        return dt.strftime("%Y-%m-%d %H:%M CAT")
    except Exception:
        return str(value)


def source_line(story: dict[str, Any]) -> str:
    detail = story.get("source_detail") or []
    if detail:
        return ", ".join(str(x) for x in detail)
    sources = story.get("sources") or []
    return ", ".join(str(x) for x in sources) if sources else "Unknown"


def story_message(idx: int, story: dict[str, Any]) -> str:
    score = story.get("total_score", "?")
    headline = story.get("headline", "Untitled")
    topic = story.get("topic", "General Economy")
    recency = story.get("time_ago", "unknown")
    angle = story.get("angle", "Broader economic implications for Zimbabwe")
    facts = story.get("key_facts") or story.get("description") or "No summary available."
    novelty = story.get("novelty_label", "New story")
    link = story.get("link", "")
    return (
        f"[{idx}] SCORE: {score}/100\n"
        f"HEADLINE: {headline}\n"
        f"SOURCES ({story.get('num_sources', len(story.get('sources') or []))}): {source_line(story)}\n"
        f"TOPIC: {topic}\n"
        f"RECENCY: {recency}\n"
        f"ANGLE: {angle}\n"
        f"KEY FACTS: {facts}\n"
        f"NOVELTY: {novelty}"
        + (f"\nLINK: {link}" if link else "")
    )


def build_messages(data: dict[str, Any], top_n: int) -> list[dict[str, str]]:
    stories = (data.get("top_stories") or [])[:top_n]
    scan_time = parse_dt(data.get("scan_time"))
    header = (
        f"ZimRate scan complete, {scan_time}\n"
        f"Articles scanned: {data.get('total_articles', 0)}\n"
        f"Relevant economic articles: {data.get('economic_articles', 0)}\n"
        f"Clusters: {data.get('clusters', 0)}\n"
        f"Top stories: {len(stories)}"
    )
    messages = [{"kind": "header", "text": header}]
    for idx, story in enumerate(stories, 1):
        messages.append({"kind": f"story_{idx}", "text": story_message(idx, story)})
    messages.append(
        {
            "kind": "footer",
            "text": "Reply with the story number to draft, for example 'Write 1'. Say 'Write all' for all five, or 'Skip all' to skip.",
        }
    )
    return messages


def main() -> int:
    parser = argparse.ArgumentParser(description="Format ZimRate scan results for clean one-story-per-message delivery.")
    parser.add_argument("--input", default=str(DEFAULT_SCAN_RESULTS))
    parser.add_argument("--top", type=int, default=5)
    parser.add_argument("--text", action="store_true", help="Print plain separated text instead of JSON.")
    args = parser.parse_args()

    path = Path(args.input)
    if not path.exists():
        raise SystemExit(f"FAIL: scan results not found: {path}")
    data = json.loads(path.read_text(encoding="utf-8"))
    messages = build_messages(data, args.top)

    if args.text:
        for msg in messages:
            print(f"--- {msg['kind']} ---")
            print(msg["text"])
        return 0

    print(json.dumps({"messages": messages}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
