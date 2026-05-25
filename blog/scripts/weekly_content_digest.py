#!/usr/bin/env python3
"""Weekly ZimRate content intelligence digest.

Reads compact scan history and official-source watcher state to produce a clean
Telegram-ready digest. Empty stdout is never used: this is a scheduled weekly
briefing, not an alert-only watchdog.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

CAT = timezone(timedelta(hours=2))
HISTORY_JSONL = Path("/opt/forexzim/blog/scan_history.jsonl")
OFFICIAL_STATE = Path("/opt/forexzim/monitor/.official_source_watch_state.json")
PUBLISHED_TOPICS = Path("/opt/forexzim/blog/published_topics.json")


def parse_dt(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        return datetime.fromisoformat(str(value)).astimezone(CAT)
    except Exception:
        return None


def load_history(days: int) -> list[dict[str, Any]]:
    if not HISTORY_JSONL.exists():
        return []
    cutoff = datetime.now(CAT) - timedelta(days=days)
    rows: list[dict[str, Any]] = []
    for line in HISTORY_JSONL.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError:
            continue
        dt = parse_dt(row.get("scan_time"))
        if dt and dt >= cutoff:
            row["_dt"] = dt
            rows.append(row)
    return rows


def load_json(path: Path, fallback):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return fallback


def story_key(story: dict[str, Any]) -> str:
    link = story.get("link")
    if link:
        return str(link)
    return str(story.get("headline", "")).lower().strip()


def summarize_stories(rows: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]], Counter]:
    by_key: dict[str, dict[str, Any]] = {}
    occurrences: Counter = Counter()
    topic_counter: Counter = Counter()

    for row in rows:
        for story in row.get("top_stories", []) or []:
            key = story_key(story)
            occurrences[key] += 1
            topic = story.get("topic") or "General Economy"
            topic_counter[topic] += 1
            current = by_key.get(key)
            score = int(story.get("total_score") or 0)
            if not current or score > int(current.get("total_score") or 0):
                by_key[key] = dict(story)
                by_key[key]["seen_count"] = occurrences[key]

    for key, story in by_key.items():
        story["seen_count"] = occurrences[key]

    strongest = sorted(by_key.values(), key=lambda s: (int(s.get("total_score") or 0), s.get("seen_count", 0)), reverse=True)[:5]
    missed = [s for s in by_key.values() if (s.get("draft_readiness") in {"Ready to draft", "Needs verification"} or int(s.get("total_score") or 0) >= 70)]
    missed = sorted(missed, key=lambda s: (s.get("seen_count", 0), int(s.get("total_score") or 0)), reverse=True)[:5]
    return strongest, missed, topic_counter


def summarize_sources(rows: list[dict[str, Any]]) -> list[str]:
    stats: dict[str, Counter] = defaultdict(Counter)
    totals: Counter = Counter()
    for row in rows:
        for source, info in (row.get("source_stats") or {}).items():
            method = info.get("method", "unknown")
            total = int(info.get("total") or 0)
            stats[source][method] += 1
            totals[source] += total
    ranked = sorted(totals, key=lambda s: (totals[s], sum(stats[s].values())), reverse=True)
    lines = []
    for source in ranked[:6]:
        methods = ", ".join(f"{k}: {v}" for k, v in stats[source].most_common())
        lines.append(f"- {source}: {totals[source]} fetched, {methods}")
    return lines


def summarize_search_console(days: int) -> list[str]:
    script = Path("/opt/forexzim/blog/scripts/search_console_digest.py")
    try:
        proc = subprocess.run(
            [sys.executable, str(script), "--days", str(max(days, 28))],
            text=True,
            capture_output=True,
            timeout=60,
        )
        output = (proc.stdout or proc.stderr or "").strip()
        if not output:
            return ["Search Console", "- No output from Search Console digest script."]
        return output.splitlines()
    except Exception as exc:
        return ["Search Console", f"- Error: {type(exc).__name__}: {exc}"]


def summarize_official_state() -> list[str]:
    state = load_json(OFFICIAL_STATE, {})
    sources = state.get("sources") or state.get("seen") or {}
    if not sources:
        return ["- No official-source state available yet."]
    lines = []
    if isinstance(sources, dict):
        for name, info in list(sources.items())[:5]:
            if isinstance(info, dict):
                last = info.get("last_seen") or info.get("last_success") or info.get("last_title") or "tracked"
                failures = info.get("failures") or info.get("failure_count") or 0
                lines.append(f"- {name}: {last}, failures: {failures}")
            else:
                lines.append(f"- {name}: tracked")
    return lines or ["- Official-source monitor active, no notable state changes."]


def recent_published(days: int) -> list[str]:
    cutoff = datetime.now(timezone.utc) - timedelta(days=days)
    topics = load_json(PUBLISHED_TOPICS, [])
    out = []
    for item in topics:
        dt = parse_dt(item.get("published_at"))
        if dt and dt.astimezone(timezone.utc) >= cutoff:
            slug = item.get("slug") or "unknown"
            kw = item.get("primary_keyword") or "topic"
            out.append(f"- {slug}: {kw}")
    return out[:5]


def story_line(story: dict[str, Any]) -> str:
    blockers = story.get("readiness_blockers") or []
    blocker_text = f" ({'; '.join(blockers[:2])})" if blockers else ""
    return f"- {story.get('total_score', '?')}/100, {story.get('draft_readiness', 'Unknown')}: {story.get('headline', 'Untitled')}{blocker_text}"


def build_digest(days: int) -> str:
    rows = load_history(days)
    now = datetime.now(CAT)
    if not rows:
        return (
            f"ZimRate weekly content intelligence, {now:%d %b %Y}\n\n"
            "No scan history found yet. Phase 4 history capture is now enabled, so the next scans will populate this digest."
        )

    strongest, missed, topics = summarize_stories(rows)
    scans = len(rows)
    total_articles = sum(int(r.get("total_articles") or 0) for r in rows)
    econ_articles = sum(int(r.get("economic_articles") or 0) for r in rows)
    clusters = sum(int(r.get("clusters") or 0) for r in rows)

    lines = [
        f"ZimRate weekly content intelligence, {now:%d %b %Y}",
        "",
        "Summary",
        f"- Scans reviewed: {scans}",
        f"- Articles fetched: {total_articles}",
        f"- Relevant economic articles: {econ_articles}",
        f"- Story clusters: {clusters}",
        "",
        "Top recurring themes",
    ]
    if topics:
        lines.extend([f"- {topic}: {count}" for topic, count in topics.most_common(5)])
    else:
        lines.append("- No themes yet.")

    lines.extend(["", "Strongest candidates"])
    lines.extend([story_line(s) for s in strongest] or ["- No candidates captured."])

    lines.extend(["", "Missed or follow-up opportunities"])
    lines.extend([story_line(s) for s in missed] or ["- No high-score missed opportunities this period."])

    lines.extend(["", "Source reliability snapshot"])
    lines.extend(summarize_sources(rows) or ["- No source stats captured."])

    lines.extend(["", "Official-source monitor"])
    lines.extend(summarize_official_state())

    lines.append("")
    lines.extend(summarize_search_console(days))

    published = recent_published(days)
    lines.extend(["", "Recently published topics"])
    lines.extend(published or ["- No published-topic entries found for this period."])

    lines.extend([
        "",
        "Recommended next moves",
        "- Draft only stories marked Ready to draft, or Needs verification after source corroboration.",
        "- Use repeated Monitor only stories as signal for background explainers, not immediate news posts.",
        "- Use Search Console query/page data to pick CTR fixes and supporting content opportunities.",
    ])
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="Build weekly ZimRate content intelligence digest.")
    parser.add_argument("--days", type=int, default=7)
    parser.add_argument("--output", help="Optional file path to write digest.")
    args = parser.parse_args()
    digest = build_digest(args.days)
    if args.output:
        Path(args.output).write_text(digest + "\n", encoding="utf-8")
    print(digest)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
