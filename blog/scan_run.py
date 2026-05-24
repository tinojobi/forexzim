#!/usr/bin/env python3
"""ZimRate Source Scanner - Full scan pipeline."""

import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone
from email.utils import parsedate_to_datetime
from urllib.parse import quote_plus
from collections import defaultdict
from difflib import SequenceMatcher
import time

CAT = timezone(timedelta(hours=2))
NOW = datetime.now(CAT)
WINDOW_START = NOW - timedelta(hours=24)

# Load published topics for novelty check
PUBLISHED_FILE = "/opt/forexzim/blog/published_topics.json"
try:
    with open(PUBLISHED_FILE) as f:
        published_topics = json.load(f)
except:
    published_topics = []

PUBLISHED_KEYWORDS = [t["primary_keyword"].lower() for t in published_topics]
PUBLISHED_SLUGS = [t.get("slug", "") for t in published_topics]

all_articles = []
source_stats = {}


def fetch_url(url, timeout=15):
    """Fetch URL with default curl UA (no custom User-Agent)."""
    try:
        result = subprocess.run(
            ["curl", "-sL", "--max-time", str(timeout), "-o", "-", url],
            capture_output=True, text=True, timeout=timeout + 5
        )
        return result.stdout if result.returncode == 0 else None
    except:
        return None


def looks_like_xml_feed(data):
    """Return True only when fetched content looks like an XML/RSS/Atom feed."""
    if not data:
        return False
    head = data[:500].lower()
    return '<?xml' in head or '<rss' in head or '<feed' in head


def parse_date(date_str):
    """Parse various date formats into timezone-aware datetime."""
    if not date_str:
        return None
    date_str = date_str.strip()
    
    # Try RFC 2822 (RSS pubDate)
    formats = [
        "%a, %d %b %Y %H:%M:%S %z",
        "%a, %d %b %Y %H:%M:%S %Z",
    ]
    for fmt in formats:
        try:
            dt = datetime.strptime(date_str, fmt)
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
            return dt
        except:
            pass
    
    # Try ISO 8601
    try:
        # Handle Z suffix
        s = date_str.replace("Z", "+00:00")
        dt = datetime.fromisoformat(s)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt
    except:
        pass
    
    # Try common alternative formats
    alt_formats = [
        "%Y-%m-%dT%H:%M:%S%z",
        "%Y-%m-%dT%H:%M:%SZ",
        "%Y-%m-%d %H:%M:%S",
    ]
    for fmt in alt_formats:
        try:
            dt = datetime.strptime(date_str, fmt)
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
            return dt
        except:
            pass
    
    return None


def extract_date(item):
    """Extract date from RSS item, checking multiple tags."""
    # Check RSS 2.0 tags
    for tag in ["pubDate", "published", "updated", "dc:date", "{http://purl.org/dc/elements/1.1/}date"]:
        el = item.find(tag)
        if el is not None and el.text:
            dt = parse_date(el.text)
            if dt:
                return dt
    
    # Check Atom entry
    for tag in ["{http://www.w3.org/2005/Atom}published", "{http://www.w3.org/2005/Atom}updated"]:
        el = item.find(tag)
        if el is not None and el.text:
            dt = parse_date(el.text)
            if dt:
                return dt
    
    return None


def parse_rss(xml_text, source_name, source_url):
    """Parse RSS feed and extract articles."""
    articles = []
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError as e:
        print(f"  XML parse error for {source_name}: {e}")
        return articles
    
    # Try RSS 2.0 items
    items = root.findall(".//item")
    if not items:
        # Try Atom entries
        items = root.findall(".//{http://www.w3.org/2005/Atom}entry")
    if not items:
        # Try RDF
        items = root.findall(".//{http://purl.org/rss/1.0/}item")
    
    for item in items:
        title_el = item.find("title")
        if title_el is None:
            title_el = item.find("{http://www.w3.org/2005/Atom}title")
        if title_el is None:
            continue
        title = (title_el.text or "").strip()
        if not title:
            continue
        
        link_el = item.find("link")
        if link_el is None:
            link_el = item.find("{http://www.w3.org/2005/Atom}link")
        link = ""
        if link_el is not None:
            link = (link_el.text or link_el.get("href", "")).strip()
        
        desc_el = item.find("description")
        if desc_el is None:
            desc_el = item.find("{http://www.w3.org/2005/Atom}summary")
        if desc_el is None:
            desc_el = item.find("{http://www.w3.org/2005/Atom}content")
        desc = ""
        if desc_el is not None and desc_el.text:
            desc = desc_el.text[:500]
        
        pub_date = extract_date(item)
        
        articles.append({
            "title": title,
            "link": link,
            "description": desc,
            "source": source_name,
            "pub_date": pub_date,
            "source_url": source_url,
        })
    
    return articles


def extract_google_news_articles(xml_text, source_name):
    """Extract articles from Google News RSS."""
    articles = []
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError:
        return articles
    
    items = root.findall(".//item")
    for item in items:
        title_el = item.find("title")
        if title_el is None:
            continue
        title = (title_el.text or "").strip()
        # Google News titles often have source appended after " - "
        headline = title.split(" - ")[0].strip() if " - " in title else title
        
        link_el = item.find("link")
        link = (link_el.text or "").strip() if link_el is not None else ""
        
        pub_date = extract_date(item)
        
        articles.append({
            "title": headline,
            "link": link,
            "description": "",
            "source": source_name,
            "pub_date": pub_date,
            "source_url": f"Google News ({source_name})",
        })
    
    return articles


# ============================================================
# TIER 1: RSS Sources
# ============================================================

print("=" * 60)
print(f"ZimRate Scan: {NOW.strftime('%Y-%m-%d %H:%M CAT')}")
print(f"Window: {WINDOW_START.strftime('%Y-%m-%d %H:%M')} to {NOW.strftime('%Y-%m-%d %H:%M')}")
print("=" * 60)

# --- iHarare ---
print("\n[1/9] iHarare...")
rss_url = "https://iharare.com/feed/"
data = fetch_url(rss_url)
if data:
    arts = parse_rss(data, "iHarare", rss_url)
    print(f"  Parsed {len(arts)} articles")
    all_articles.extend(arts)
    source_stats["iHarare"] = {"total": len(arts), "method": "RSS"}
else:
    print("  FAILED - no response")
    source_stats["iHarare"] = {"total": 0, "method": "FAILED"}

# --- ZimLive ---
print("\n[2/9] ZimLive...")
rss_url = "https://www.zimlive.com/feed/"
data = fetch_url(rss_url)
if data:
    arts = parse_rss(data, "ZimLive", rss_url)
    print(f"  Parsed {len(arts)} articles")
    all_articles.extend(arts)
    source_stats["ZimLive"] = {"total": len(arts), "method": "RSS"}
else:
    print("  FAILED - no response")
    source_stats["ZimLive"] = {"total": 0, "method": "FAILED"}

# --- New Zimbabwe (unreliable, try Google News fallback) ---
print("\n[3/9] New Zimbabwe...")
nz_rss_urls = [
    "https://www.newzimbabwe.com/feed/",
    "https://www.newzimbabwe.com/arc/outboundfeeds/rss/",
]
got_nz = False
for url in nz_rss_urls:
    data = fetch_url(url)
    if looks_like_xml_feed(data):
        arts = parse_rss(data, "New Zimbabwe", url)
        if arts:
            print(f"  RSS OK: {len(arts)} articles")
            all_articles.extend(arts)
            source_stats["New Zimbabwe"] = {"total": len(arts), "method": "RSS"}
            got_nz = True
            break
if not got_nz:
    print("  RSS failed, trying Google News fallback...")
    gn_url = f"https://news.google.com/rss/search?q=site%3Anewzimbabwe.com+Zimbabwe&hl=en-ZW&gl=ZW&ceid=ZW:en"
    data = fetch_url(gn_url)
    if data:
        arts = extract_google_news_articles(data, "New Zimbabwe")
        print(f"  Google News: {len(arts)} articles")
        all_articles.extend(arts)
        source_stats["New Zimbabwe"] = {"total": len(arts), "method": "Google News"}
    else:
        source_stats["New Zimbabwe"] = {"total": 0, "method": "FAILED"}

# --- Zimbabwe Mail (unreliable, try Google News fallback) ---
print("\n[4/9] Zimbabwe Mail...")
zm_rss_urls = [
    "https://www.thezimbabwemail.com/feed/",
    "https://thezimbabwemail.com/feed/",
]
got_zm = False
for url in zm_rss_urls:
    data = fetch_url(url)
    if data and ('<?xml' in data[:500].lower() or '<rss' in data[:500].lower()):
        arts = parse_rss(data, "Zimbabwe Mail", url)
        if arts:
            print(f"  RSS OK: {len(arts)} articles")
            all_articles.extend(arts)
            source_stats["Zimbabwe Mail"] = {"total": len(arts), "method": "RSS"}
            got_zm = True
            break
if not got_zm:
    print("  RSS failed, trying Google News fallback...")
    gn_url = f"https://news.google.com/rss/search?q=site%3Athezimbabwemail.com+Zimbabwe&hl=en-ZW&gl=ZW&ceid=ZW:en"
    data = fetch_url(gn_url)
    if data:
        arts = extract_google_news_articles(data, "Zimbabwe Mail")
        print(f"  Google News: {len(arts)} articles")
        all_articles.extend(arts)
        source_stats["Zimbabwe Mail"] = {"total": len(arts), "method": "Google News"}
    else:
        source_stats["Zimbabwe Mail"] = {"total": 0, "method": "FAILED"}


# ============================================================
# TIER 2: AMH Sites (Broken RSS → Google News fallback)
# ============================================================

amh_sites = [
    {
        "name": "NewsDay",
        "rss_urls": [
            "https://www.newsday.co.zw/feed/",
            "https://www.newsday.co.zw/business/feed/",
            "https://newsday.co.zw/feed/",
            "https://newsday.co.zw/business/feed/",
        ],
        "gn_query": "site:newsday.co.zw Zimbabwe",
    },
    {
        "name": "Herald",
        "rss_urls": [
            "https://www.heraldonline.co.zw/feed/",
            "https://www.herald.co.zw/feed/",
            "https://www.herald.co.zw/category/business/feed/",
            "https://heraldonline.co.zw/feed/",
            "https://herald.co.zw/feed/",
            "https://herald.co.zw/category/business/feed/",
        ],
        "gn_query": "site:herald.co.zw Zimbabwe",
    },
    {
        "name": "Zimbabwe Independent",
        "rss_urls": [
            "https://www.theindependent.co.zw/feed/",
            "https://www.theindependent.co.zw/business-digest/feed/",
            "https://theindependent.co.zw/feed/",
            "https://theindependent.co.zw/business-digest/feed/",
        ],
        "gn_query": "site:theindependent.co.zw Zimbabwe",
    },
    {
        "name": "Southern Eye",
        "rss_urls": [
            "https://www.southerneye.co.zw/feed/",
            "https://southerneye.co.zw/feed/",
        ],
        "gn_query": "site:southerneye.co.zw Zimbabwe",
    },
    {
        "name": "The Standard",
        "rss_urls": [
            "https://www.thestandard.co.zw/feed/",
            "https://www.thestandard.co.zw/business/feed/",
            "https://thestandard.co.zw/feed/",
            "https://thestandard.co.zw/business/feed/",
        ],
        "gn_query": "site:thestandard.co.zw Zimbabwe",
    },
]

for i, site in enumerate(amh_sites):
    idx = 5 + i
    print(f"\n[{idx}/9] {site['name']}...")
    
    got_rss = False
    for url in site["rss_urls"]:
        data = fetch_url(url)
        if looks_like_xml_feed(data):
            arts = parse_rss(data, site["name"], url)
            if arts:
                print(f"  RSS OK: {len(arts)} articles")
                all_articles.extend(arts)
                source_stats[site["name"]] = {"total": len(arts), "method": "RSS"}
                got_rss = True
                break
    
    if not got_rss:
        print(f"  RSS failed, trying Google News...")
        encoded_q = quote_plus(site["gn_query"])
        gn_url = f"https://news.google.com/rss/search?q={encoded_q}&hl=en-ZW&gl=ZW&ceid=ZW:en"
        data = fetch_url(gn_url)
        if data:
            arts = extract_google_news_articles(data, site["name"])
            print(f"  Google News: {len(arts)} articles")
            all_articles.extend(arts)
            source_stats[site["name"]] = {"total": len(arts), "method": "Google News"}
        else:
            source_stats[site["name"]] = {"total": 0, "method": "FAILED"}


# ============================================================
# FILTER: Within 24h window, extract relevant economic articles
# ============================================================

print("\n" + "=" * 60)
print("FILTERING & SCORING")
print("=" * 60)

# Economic keywords — broadened to catch more stories
ECON_KEYWORDS = [
    # Currency & monetary
    "zig", "zimbabwe gold", "forex", "exchange rate", "currency", "rbz", "reserve bank",
    "inflation", "interest rate", "monetary", "money supply", "central bank",
    "parallel market", "black market", "usd", "us dollar", "dollar",
    # Mining & minerals
    "mining", "mineral", "gold", "platinum", "lithium", "chrome", "diamond",
    "tobacco", "coal", "iron ore", "copper",
    # Trade & agriculture
    "agriculture", "export", "import", "trade", "fiscal", "budget",
    "crop", "harvest", "maize", "wheat", "livestock",
    # Tax & revenue
    "tax", "zimra", "revenue", "duty", "levy", "tariff",
    # GDP & economy
    "gdp", "economy", "economic", "finance", "treasury", "growth",
    "recession", "depression", "recovery",
    # Labour
    "wage", "salary", "pension", "compensation", "labour", "labor",
    "employment", "unemployment", "job", "jobs", "strike",
    # Energy
    "fuel", "energy", "electricity", "zesa", "petrol", "diesel", "solar",
    # Banking & telecoms
    "telecommunication", "econet", "netone", "telecel", "banking", "ecocash",
    "bank", "insurance", "microfinance", "mobile money",
    # Markets
    "stock", "securities", "zse", "bond", "bond market", "shares", "equity",
    # Debt & international
    "debt", "south africa", "sadc", "comesa", "afreximbank", "world bank", "imf",
    # Investment
    "investment", "fdi", "privatisation", "privatization", "ppp", "concession",
    # Industry
    "manufacturing", "industry", "industrialisation", "industrialization",
    "production", "output", "capacity",
    # Infrastructure
    "supply chain", "logistics", "port", "beira", "durban", "rail", "road",
    "water", "dam", "infrastructure",
    # Social & development
    "poverty", "human development", "hdz", "cost of living",
    # Climate & environment (economic impact)
    "climate change", "drought", "flood", "el nino",
    # Regional trade
    "african continental free trade", "afcfta", "bilateral",
    # Privatisation & reforms
    "state enterprise", "soe", "reform", "deregulation", "liberalisation",
]

EXCLUDE_PATTERNS = [
    "celebrity", "gossip", "murder", "robbery", "court", "trial", "rape",
    "football", "soccer", "church", "prophet", "relationship", "wedding",
    "accident", "dies", "killed", "arrested", "music", "movie", "psl",
    "golden boot", "caps united", "zifa", "stadium", "bangladesh",
    # Crime/court stories often contain fee, tax, dollar or gold words but are not
    # useful ZimRate economic coverage unless the policy/business angle is central.
    "behind bars", "police officer", "accomplices", "fake release fees",
    "land police", "fraudster", "fraud", "scam", "scammer", "thief", "theft",
]

CONCRETE_ECON_HOOKS = [
    "zig", "forex", "exchange rate", "rbz", "reserve bank", "zimra", "tax",
    "fuel", "petrol", "diesel", "price", "prices", "inflation", "interest rate",
    "bank", "lending", "credit", "usd", "dollar", "export", "import", "mining",
    "mineral", "gold", "platinum", "lithium", "diamond", "budget", "fiscal",
    "debt", "imf", "world bank", "wage", "salary", "pension", "revenue",
    "us$", "$", "million", "billion", "manufacturing", "investor", "investors", "plant",
]

def is_economic(title, desc=""):
    """Check if article is economics-related, excluding weak keyword-only matches."""
    text = (title + " " + desc).lower()
    if any(pattern in text for pattern in EXCLUDE_PATTERNS):
        return False
    # Generic words like business/economic/agriculture caused false positives.
    # Require at least one concrete money, policy, market, tax, banking or trade hook.
    if not any(hook in text for hook in CONCRETE_ECON_HOOKS):
        return False
    for kw in ECON_KEYWORDS:
        if kw in text:
            return True
    return False


def time_ago(pub_date):
    """Calculate human-readable time ago."""
    diff = NOW - pub_date
    hours = diff.total_seconds() / 3600
    if hours < 1:
        mins = int(diff.total_seconds() / 60)
        return f"{mins}m ago"
    elif hours < 24:
        return f"{int(hours)}h ago"
    else:
        days = int(hours / 24)
        return f"{days}d ago"


def recency_score(pub_date):
    """Score 0-20 based on recency."""
    hours = (NOW - pub_date).total_seconds() / 3600
    if hours <= 6:
        return 19 + min(1, (6 - hours) / 6)  # 19-20
    elif hours <= 12:
        return 16 + 3 * (12 - hours) / 6  # 16-18
    elif hours <= 18:
        return 13 + 3 * (18 - hours) / 6  # 13-15
    elif hours <= 24:
        return 10 + 3 * (24 - hours) / 6  # 10-12
    return 5


def topic_score(title, desc=""):
    """Score 0-25 based on topic match."""
    text = (title + " " + desc).lower()
    direct_kw = ["zig", "forex", "exchange rate", "currency", "rbz", "reserve bank",
                  "mining", "gold", "platinum", "lithium", "zimra", "tax"]
    for kw in direct_kw:
        if kw in text:
            return 22 + min(3, 5)
    
    related_kw = ["inflation", "interest rate", "export", "import", "trade",
                   "fiscal", "budget", "revenue", "gdp", "economy", "finance",
                   "wage", "salary", "fuel", "energy", "banking", "investment",
                   "manufacturing", "industry"]
    for kw in related_kw:
        if kw in text:
            return 17 + min(4, 6)
    
    tangential_kw = ["poverty", "employment", "water", "crop", "supply"]
    for kw in tangential_kw:
        if kw in text:
            return 12 + min(4, 4)
    
    return 10


def search_interest_score(title, desc=""):
    """Score 0-15 based on likely search interest."""
    text = (title + " " + desc).lower()
    high_kw = ["zig", "fuel price", "wage", "salary", "inflation", "exchange rate"]
    for kw in high_kw:
        if kw in text:
            return 13 + min(2, 3)
    
    med_kw = ["trade", "agriculture", "mining", "gold", "banking", "investment"]
    for kw in med_kw:
        if kw in text:
            return 9 + min(3, 3)
    
    low_kw = ["policy", "regulation", "report", "plan"]
    for kw in low_kw:
        if kw in text:
            return 5 + min(3, 3)
    
    return 8


def normalize_topic_text(text):
    """Normalize common Zimbabwe forex synonyms for novelty matching."""
    text = text.lower()
    replacements = {
        "black market": "parallel market",
        "us dollars": "forex",
        "us dollar": "forex",
        "dollars": "forex",
        "dollar": "forex",
        "zig": "zig",
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
    return text


def novelty_score(title, desc=""):
    """Score 0-10 based on novelty vs published topics."""
    text = normalize_topic_text(title + " " + desc)
    
    for kw in PUBLISHED_KEYWORDS:
        # Check similarity, with common forex synonyms normalized.
        words_pub = set(normalize_topic_text(kw).split())
        words_art = set(text.split())
        overlap = words_pub & words_art
        if len(overlap) >= 2:
            # Already published similar topic
            return 2
    
    # Check for similar headlines
    for slug in PUBLISHED_SLUGS:
        slug_words = slug.replace("-", " ").split()
        art_words = text.split()
        common = set(slug_words) & set(art_words)
        if len(common) >= 3:
            return 3
    
    return 8


def cluster_key(title):
    """Generate a cluster key for grouping similar articles."""
    # Simple word-based clustering
    words = re.findall(r'\b\w+\b', title.lower())
    # Remove common words
    stop = {"the", "a", "an", "in", "on", "at", "to", "for", "of", "and", "or",
            "is", "are", "was", "were", "be", "been", "being", "have", "has", "had",
            "do", "does", "did", "will", "would", "could", "should", "may", "might",
            "shall", "can", "need", "dare", "ought", "used", "with", "from", "by",
            "zimbabwe", "zim", "says", "new", "over", "into", "after", "as", "its",
            "their", "about", "more", "that", "this", "than", "other", "but", "not",
            "all", "some", "any", "each", "every", "both", "few", "most", "no"}
    words = [w for w in words if w not in stop and len(w) > 2]
    return " ".join(sorted(words)[:5])


# Filter and score
filtered = []
cluster_map = defaultdict(list)

for art in all_articles:
    # Check if within 24h window
    if art["pub_date"] is None:
        continue
    if art["pub_date"] < WINDOW_START:
        continue
    
    # Check if economic
    if not is_economic(art["title"], art.get("description", "")):
        continue
    
    # Score
    rs = recency_score(art["pub_date"])
    ts = topic_score(art["title"], art.get("description", ""))
    sis = search_interest_score(art["title"], art.get("description", ""))
    ns = novelty_score(art["title"], art.get("description", ""))
    
    # Source score will be calculated after clustering
    art["_recency"] = rs
    art["_topic"] = ts
    art["_search"] = sis
    art["_novelty"] = ns
    art["_time_ago"] = time_ago(art["pub_date"])
    
    # Cluster key
    ckey = cluster_key(art["title"])
    art["_cluster_key"] = ckey
    cluster_map[ckey].append(art)
    filtered.append(art)

print(f"\nTotal articles fetched: {len(all_articles)}")

# Debug: show all articles with their dates and economic status
economic_count = 0
window_count = 0
no_date_count = 0
old_count = 0
non_econ_count = 0
for art in all_articles:
    if art["pub_date"] is None:
        no_date_count += 1
        continue
    if art["pub_date"] < WINDOW_START:
        old_count += 1
        continue
    window_count += 1
    if is_economic(art["title"], art.get("description", "")):
        economic_count += 1
    else:
        non_econ_count += 1
        # Print first 15 non-economic articles for debugging
        if non_econ_count <= 15:
            print(f"  NON-ECON: {art['source']}: {art['title'][:80]}")

print(f"\nDebug: {no_date_count} no date, {old_count} outside window, {window_count} in window")
print(f"Debug: {economic_count} economic, {non_econ_count} non-economic (in window)")
print(f"Within 24h window + economic: {len(filtered)}")

# Source stats
for src, info in sorted(source_stats.items()):
    print(f"  {src}: {info['total']} ({info['method']})")

# ============================================================
# SCORING WITH SOURCE CLUSTERING
# ============================================================

# For each cluster, calculate source score (max 30)
clustered = []
seen_clusters = set()

for ckey, arts in sorted(cluster_map.items(), key=lambda x: -max(a["_recency"] for a in x[1])):
    if ckey in seen_clusters:
        continue
    seen_clusters.add(ckey)
    
    # Unique sources in cluster
    unique_sources = set(a["source"] for a in arts)
    num_sources = len(unique_sources)
    
    if num_sources >= 3:
        source_s = 27 + min(3, num_sources - 2)
    elif num_sources == 2:
        source_s = 22 + min(3, 3)
    else:
        source_s = 16 + min(4, 4)
    
    # Get the best article from the cluster (most recent)
    best = max(arts, key=lambda a: a["pub_date"])
    
    # Rubric: each component already carries its proportional weight
    # Sources (30), Topic (25), Recency (20), Search (15), Novelty (10)
    total_score = (
        source_s +
        best["_topic"] +
        best["_recency"] +
        best["_search"] +
        best["_novelty"]
    )
    
    total_score = min(100, int(total_score))
    
    clustered.append({
        "headline": best["title"],
        "link": best["link"],
        "sources": list(unique_sources),
        "num_sources": num_sources,
        "source_detail": [f"{a['source']} ({a['_time_ago']})" for a in arts],
        "topic_score": best["_topic"],
        "recency_score": best["_recency"],
        "search_score": best["_search"],
        "novelty_score": best["_novelty"],
        "source_score": source_s,
        "total_score": total_score,
        "time_ago": best["_time_ago"],
        "pub_date": best["pub_date"],
        "description": best.get("description", ""),
        "cluster_size": len(arts),
    })

# Sort by total score
clustered.sort(key=lambda x: -x["total_score"])

# Take top 5, preferring stories not already covered in the novelty window.
# Repetitive stories can still fill empty slots, but they should not displace
# fresh candidates when the scan has enough usable options.
fresh_clusters = [s for s in clustered if s.get("novelty_score", 0) > 3]
repeat_clusters = [s for s in clustered if s.get("novelty_score", 0) <= 3]
top5 = (fresh_clusters + repeat_clusters)[:5]

print(f"\nClusters found: {len(clustered)}")
print(f"Top stories:")
for i, s in enumerate(top5):
    print(f"  [{i+1}] {s['total_score']}/100 - {s['headline'][:80]}")
    print(f"      Sources: {', '.join(s['sources'])} | Recency: {s['time_ago']}")


# ============================================================
# TOPIC CATEGORIZATION
# ============================================================

def categorize_topic(title, desc=""):
    text = (title + " " + desc).lower()
    if any(w in text for w in ["zig", "zimbabwe gold", "gold reserve", "rbz", "reserve bank"]):
        return "ZiG / Monetary Policy"
    if any(w in text for w in ["mining", "mineral", "platinum", "lithium", "chrome", "diamond"]):
        return "Mining & Minerals"
    if any(w in text for w in ["forex", "exchange rate", "currency", "usd", "dollar", "parallel market", "black market"]):
        return "Forex & Exchange Rates"
    if any(w in text for w in ["tax", "zimra", "revenue", "fiscal", "budget"]):
        return "Tax & Fiscal Policy"
    if any(w in text for w in ["wage", "salary", "pension", "compensation"]):
        return "Wages & Employment"
    if any(w in text for w in ["fuel", "energy", "electricity", "zesa", "petrol"]):
        return "Energy & Fuel"
    if any(w in text for w in ["trade", "export", "import", "sadc", "afta"]):
        return "Trade & Regional"
    if any(w in text for w in ["inflation", "interest rate", "monetary"]):
        return "Inflation & Interest Rates"
    if any(w in text for w in ["bank", "banking", "ecocash", "financial"]):
        return "Banking & Finance"
    if any(w in text for w in ["manufacturing", "industry", "production"]):
        return "Manufacturing & Industry"
    if any(w in text for w in ["agriculture", "crop", "tobacco", "maize"]):
        return "Agriculture"
    if any(w in text for w in ["investment", "fdi", "privat"]):
        return "Investment"
    if any(w in text for w in ["poverty", "social", "health", "education"]):
        return "Social & Development"
    return "General Economy"


def novelty_label(title, desc=""):
    text = normalize_topic_text(title + " " + desc)
    
    for kw in PUBLISHED_KEYWORDS:
        words_pub = set(normalize_topic_text(kw).split())
        words_art = set(text.split())
        overlap = words_pub & words_art
        if len(overlap) >= 2:
            return "Already published ✅"
    
    for slug in PUBLISHED_SLUGS:
        slug_words = set(slug.replace("-", " ").split())
        art_words = set(text.split())
        common = slug_words & art_words
        if len(common) >= 3:
            return "Related to recent coverage"
    
    return "New story"


def suggest_angle(title, desc=""):
    text = (title + " " + desc).lower()
    angles = []
    
    if any(w in text for w in ["zig", "gold", "rbz"]):
        angles.append("ZiG stability and gold backing analysis")
    if any(w in text for w in ["mining", "mineral"]):
        angles.append("Impact on Zimbabwe's mining export revenue")
    if any(w in text for w in ["forex", "exchange rate", "parallel market", "black market", "usd", "dollar"]):
        angles.append("Parallel vs official rate dynamics")
    if any(w in text for w in ["inflation"]):
        angles.append("Inflation trajectory and purchasing power")
    if any(w in text for w in ["tax", "zimra"]):
        angles.append("Revenue mobilisation and business environment")
    if any(w in text for w in ["wage", "salary"]):
        angles.append("Wage erosion and cost of living")
    if any(w in text for w in ["fuel", "energy"]):
        angles.append("Energy costs and industrial competitiveness")
    if any(w in text for w in ["trade", "export"]):
        angles.append("Export performance and trade balance")
    
    if angles:
        return "; ".join(angles[:2])
    return "Broader economic implications for Zimbabwe"


# Assign topic, novelty, angle
for s in top5:
    s["topic"] = categorize_topic(s["headline"], s.get("description", ""))
    s["novelty_label"] = novelty_label(s["headline"], s.get("description", ""))
    s["angle"] = suggest_angle(s["headline"], s.get("description", ""))
    s["key_facts"] = f"Headline: {s['headline']}. Covered by {s['num_sources']} source(s). {s['cluster_size']} article(s) in cluster."

# Save results for pipeline
output = {
    "scan_time": NOW.isoformat(),
    "window_start": WINDOW_START.isoformat(),
    "source_stats": source_stats,
    "total_articles": len(all_articles),
    "economic_articles": len(filtered),
    "clusters": len(clustered),
    "all_clusters": clustered,
    "top_stories": top5,
}

with open("/opt/forexzim/blog/scan_results.json", "w") as f:
    json.dump(output, f, indent=2, default=str)

print(f"\nResults saved to scan_results.json")
print("Scan complete.")
