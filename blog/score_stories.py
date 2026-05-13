"""
ZimRate Story Scoring Module
Ranks clustered news stories to decide which are worth writing about.

Usage:
    from score_stories import score_story, rank_stories
    ranked = rank_stories(clustered_stories, published_topics)
"""

import json
from datetime import datetime, timedelta
from pathlib import Path

# Core topics ZimRate cares about. Add/remove as priorities shift.
CORE_TOPICS = {
    "zig": 1.0,
    "exchange rate": 1.0,
    "forex": 1.0,
    "inflation": 1.0,
    "rbz": 0.9,
    "reserve bank": 0.9,
    "zimra": 0.9,
    "tax": 0.8,
    "remittance": 0.8,
    "diaspora": 0.7,
    "mining": 0.7,
    "agriculture": 0.6,
    "tobacco": 0.6,
    "budget": 0.8,
    "fuel price": 0.8,
    "interest rate": 0.8,
    "parallel market": 0.9,
    "bond note": 0.7,
    "monetary policy": 0.9,
}

# Weights must sum to 1.0
WEIGHTS = {
    "sources": 0.30,
    "topic_match": 0.25,
    "recency": 0.20,
    "search_interest": 0.15,
    "novelty": 0.10,
}

PUBLISHED_TOPICS_PATH = Path("/opt/forexzim/blog/published_topics.json")
NOVELTY_WINDOW_DAYS = 14


def score_sources(num_sources: int) -> float:
    """1 source = 0.3, 2 = 0.6, 3 = 0.85, 4+ = 1.0"""
    if num_sources >= 4:
        return 1.0
    if num_sources == 3:
        return 0.85
    if num_sources == 2:
        return 0.6
    return 0.3


def score_topic_match(headline: str, summary: str) -> float:
    """Match against CORE_TOPICS, return highest weight found."""
    text = f"{headline} {summary}".lower()
    matches = [weight for keyword, weight in CORE_TOPICS.items() if keyword in text]
    if not matches:
        return 0.2  # Off-topic but not zero
    return min(max(matches), 1.0)


def score_recency(published_at: datetime) -> float:
    """Fresher = higher. 0-6h = 1.0, 6-12h = 0.8, 12-24h = 0.6, older = 0.3"""
    now = datetime.now(published_at.tzinfo) if published_at.tzinfo else datetime.now()
    hours_old = (now - published_at).total_seconds() / 3600
    if hours_old <= 6:
        return 1.0
    if hours_old <= 12:
        return 0.8
    if hours_old <= 24:
        return 0.6
    if hours_old <= 48:
        return 0.4
    return 0.2


def score_search_interest(keyword: str, trends_data: dict = None) -> float:
    """
    Placeholder for Google Trends or keyword volume data.
    If you wire up pytrends or a keyword tool, return normalized 0-1.
    Default: 0.5 (neutral) when no data available.
    """
    if not trends_data:
        return 0.5
    return trends_data.get(keyword.lower(), 0.5)


def score_novelty(topic_cluster_id: str, published_topics: list) -> float:
    """1.0 if topic not covered in last 14 days, else 0.2"""
    cutoff = datetime.now() - timedelta(days=NOVELTY_WINDOW_DAYS)
    for entry in published_topics:
        if entry.get("cluster_id") == topic_cluster_id:
            published_date = datetime.fromisoformat(entry["published_at"])
            if published_date > cutoff:
                return 0.2
    return 1.0


def score_story(story: dict, published_topics: list, trends_data: dict = None) -> dict:
    """
    Score a single clustered story.
    
    story dict shape:
    {
        "cluster_id": "zig-liquidity-may-2026",
        "headline": "RBZ tightens ZiG liquidity",
        "summary": "Central bank raises reserve requirements...",
        "sources": [{"outlet": "Herald", "url": "...", "published_at": "2026-05-12T08:00:00+02:00"}, ...],
        "primary_keyword": "ZiG liquidity"
    }
    """
    num_sources = len(story["sources"])
    earliest_pub = min(
        datetime.fromisoformat(s["published_at"]) for s in story["sources"]
    )
    
    sub_scores = {
        "sources": score_sources(num_sources),
        "topic_match": score_topic_match(story["headline"], story.get("summary", "")),
        "recency": score_recency(earliest_pub),
        "search_interest": score_search_interest(story.get("primary_keyword", ""), trends_data),
        "novelty": score_novelty(story["cluster_id"], published_topics),
    }
    
    final_score = sum(sub_scores[k] * WEIGHTS[k] for k in WEIGHTS) * 100
    
    return {
        **story,
        "sub_scores": sub_scores,
        "final_score": round(final_score, 1),
    }


def load_published_topics() -> list:
    """Load published topics log, return empty list if file missing."""
    if not PUBLISHED_TOPICS_PATH.exists():
        return []
    with open(PUBLISHED_TOPICS_PATH) as f:
        return json.load(f)


def log_published_topic(cluster_id: str, slug: str, primary_keyword: str):
    """Append a newly published topic to the log."""
    topics = load_published_topics()
    topics.append({
        "cluster_id": cluster_id,
        "slug": slug,
        "primary_keyword": primary_keyword,
        "published_at": datetime.now().isoformat(),
    })
    PUBLISHED_TOPICS_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(PUBLISHED_TOPICS_PATH, "w") as f:
        json.dump(topics, f, indent=2)


def rank_stories(clustered_stories: list, trends_data: dict = None, top_n: int = 5) -> list:
    """
    Main entry point. Takes clustered stories, returns top N ranked.
    """
    published = load_published_topics()
    scored = [score_story(s, published, trends_data) for s in clustered_stories]
    scored.sort(key=lambda x: x["final_score"], reverse=True)
    return scored[:top_n]


# Example usage
if __name__ == "__main__":
    sample_stories = [
        {
            "cluster_id": "zig-liquidity-may-2026",
            "headline": "RBZ tightens ZiG liquidity requirements",
            "summary": "Central bank raises reserve ratios for banks holding ZiG deposits",
            "primary_keyword": "ZiG liquidity",
            "sources": [
                {"outlet": "Herald", "url": "https://herald.co.zw/...", "published_at": "2026-05-12T08:00:00+02:00"},
                {"outlet": "NewsDay", "url": "https://newsday.co.zw/...", "published_at": "2026-05-12T09:30:00+02:00"},
                {"outlet": "ZimLive", "url": "https://zimlive.com/...", "published_at": "2026-05-12T10:15:00+02:00"},
            ]
        },
        {
            "cluster_id": "tobacco-auction-2026",
            "headline": "Tobacco auction floors open with higher prices",
            "summary": "Opening prices average $3.20/kg, up from last season",
            "primary_keyword": "tobacco prices Zimbabwe",
            "sources": [
                {"outlet": "NewsDay", "url": "https://newsday.co.zw/...", "published_at": "2026-05-11T14:00:00+02:00"},
            ]
        },
    ]
    
    ranked = rank_stories(sample_stories)
    for story in ranked:
        print(f"\n{story['final_score']}/100 - {story['headline']}")
        print(f"  Sources: {len(story['sources'])}")
        print(f"  Sub-scores: {story['sub_scores']}")
