#!/bin/bash
# ZimRate Health Check Watchdog
# Silent when healthy. Alerts when down. Alerts on recovery.
# DRY_RUN=1 or --dry-run prints check results without changing state/logs or emitting alerts.

STATE_FILE="/opt/forexzim/monitor/.health_state"
LOG_FILE="/opt/forexzim/monitor/health.log"

SITE_URL="https://zimrate.com"
BLOG_URL="https://zimrate.com/blog"
SSL_HOST="zimrate.com"
TIMEOUT=15

DISK_PATH="/"
DISK_WARN_PCT=90
MEM_WARN_MB=300
SSL_WARN_DAYS=14

DRY_RUN="${DRY_RUN:-0}"
if [ "${1:-}" = "--dry-run" ]; then
    DRY_RUN=1
fi

# Read previous state (0=healthy, 1=down). Do not create files/dirs in dry-run mode.
PREV_STATE=0
if [ -f "$STATE_FILE" ]; then
    PREV_STATE=$(cat "$STATE_FILE")
    if [ "$PREV_STATE" != "0" ] && [ "$PREV_STATE" != "1" ]; then
        PREV_STATE=0
    fi
fi

TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

# ── Checks ──────────────────────────────────────────────────────────────

ERRORS=""

# 1. Homepage HTTP status
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time "$TIMEOUT" "$SITE_URL" 2>/dev/null)
if [ "$HTTP_CODE" != "200" ]; then
    ERRORS="${ERRORS}• Homepage: HTTP ${HTTP_CODE}\n"
fi

# 2. Blog page
BLOG_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time "$TIMEOUT" "$BLOG_URL" 2>/dev/null)
if [ "$BLOG_CODE" != "200" ]; then
    ERRORS="${ERRORS}• Blog page: HTTP ${BLOG_CODE}\n"
fi

# 3. Java process alive
JAVA_ALIVE=$(pgrep -f "zimrate-0.0.1-SNAPSHOT.jar" > /dev/null 2>&1 && echo "yes" || echo "no")
if [ "$JAVA_ALIVE" != "yes" ]; then
    ERRORS="${ERRORS}• Java process: NOT RUNNING\n"
fi

# 4. Caddy running
CADDY_ALIVE=$(systemctl is-active caddy 2>/dev/null)
if [ "$CADDY_ALIVE" != "active" ]; then
    ERRORS="${ERRORS}• Caddy proxy: ${CADDY_ALIVE}\n"
fi

# 5. systemd service healthy
SERVICE_STATUS=$(systemctl is-active forexzim 2>/dev/null)
if [ "$SERVICE_STATUS" != "active" ]; then
    ERRORS="${ERRORS}• forexzim service: ${SERVICE_STATUS}\n"
fi

# 6. PostgreSQL reachable/listening
if command -v pg_isready >/dev/null 2>&1; then
    if pg_isready -h 127.0.0.1 -p 5432 -q 2>/dev/null; then
        POSTGRES_STATUS="ready"
    else
        POSTGRES_STATUS="not_ready"
    fi
elif ss -tln 2>/dev/null | grep -qE '127\.0\.0\.1:5432|0\.0\.0\.0:5432|\*:5432|\[::\]:5432'; then
    POSTGRES_STATUS="listening"
else
    POSTGRES_STATUS="not_listening"
fi
if [ "$POSTGRES_STATUS" != "ready" ] && [ "$POSTGRES_STATUS" != "listening" ]; then
    ERRORS="${ERRORS}• PostgreSQL: ${POSTGRES_STATUS}\n"
fi

# 7. Disk usage threshold
DISK_USED_PCT=$(df -P "$DISK_PATH" 2>/dev/null | awk 'NR==2 {gsub(/%/, "", $5); print $5}')
if [ -z "$DISK_USED_PCT" ]; then
    DISK_STATUS="unknown"
    ERRORS="${ERRORS}• Disk ${DISK_PATH}: unable to check\n"
elif [ "$DISK_USED_PCT" -ge "$DISK_WARN_PCT" ]; then
    DISK_STATUS="${DISK_USED_PCT}%"
    ERRORS="${ERRORS}• Disk ${DISK_PATH}: ${DISK_USED_PCT}% used (threshold ${DISK_WARN_PCT}%)\n"
else
    DISK_STATUS="${DISK_USED_PCT}%"
fi

# 8. Memory availability threshold
MEM_AVAILABLE_MB=$(awk '/MemAvailable:/ {printf "%d", $2/1024}' /proc/meminfo 2>/dev/null)
if [ -z "$MEM_AVAILABLE_MB" ]; then
    MEM_STATUS="unknown"
    ERRORS="${ERRORS}• Memory: unable to check\n"
elif [ "$MEM_AVAILABLE_MB" -lt "$MEM_WARN_MB" ]; then
    MEM_STATUS="${MEM_AVAILABLE_MB}MB"
    ERRORS="${ERRORS}• Memory: ${MEM_AVAILABLE_MB}MB available (threshold ${MEM_WARN_MB}MB)\n"
else
    MEM_STATUS="${MEM_AVAILABLE_MB}MB"
fi

# 9. SSL certificate expiry threshold
SSL_DAYS="unknown"
if command -v openssl >/dev/null 2>&1; then
    CERT_END_DATE=$(timeout "$TIMEOUT" bash -c "echo | openssl s_client -servername '$SSL_HOST' -connect '$SSL_HOST:443' 2>/dev/null | openssl x509 -noout -enddate 2>/dev/null" | cut -d= -f2-)
    if [ -n "$CERT_END_DATE" ]; then
        CERT_END_EPOCH=$(date -d "$CERT_END_DATE" +%s 2>/dev/null || echo "")
        NOW_EPOCH=$(date +%s)
        if [ -n "$CERT_END_EPOCH" ]; then
            SSL_DAYS=$(( (CERT_END_EPOCH - NOW_EPOCH) / 86400 ))
            if [ "$SSL_DAYS" -le "$SSL_WARN_DAYS" ]; then
                ERRORS="${ERRORS}• SSL certificate: expires in ${SSL_DAYS} days (threshold ${SSL_WARN_DAYS})\n"
            fi
        fi
    fi
fi

# ── Determine new state ─────────────────────────────────────────────────

if [ -n "$ERRORS" ]; then
    NEW_STATE=1
else
    NEW_STATE=0
fi

LOG_LINE="[$TIMESTAMP] HTTP=$HTTP_CODE BLOG=$BLOG_CODE JAVA=$JAVA_ALIVE CADDY=$CADDY_ALIVE SERVICE=$SERVICE_STATUS POSTGRES=$POSTGRES_STATUS DISK=$DISK_STATUS MEM=$MEM_STATUS SSL_DAYS=$SSL_DAYS"

if [ "$DRY_RUN" = "1" ]; then
    echo "DRY_RUN=1"
    echo "PREV_STATE=$PREV_STATE NEW_STATE=$NEW_STATE"
    echo "$LOG_LINE"
    if [ -n "$ERRORS" ]; then
        echo "ISSUES:"
        echo -e "$ERRORS"
    else
        echo "ISSUES=none"
    fi
    exit 0
fi

# ── Output logic ────────────────────────────────────────────────────────

# Ensure state dir exists for normal runs
mkdir -p "$(dirname "$STATE_FILE")"

# Save state
echo "$NEW_STATE" > "$STATE_FILE"

# Log every check (for debugging)
echo "$LOG_LINE" >> "$LOG_FILE"

# Keep log file trimmed to last 500 lines
tail -500 "$LOG_FILE" > "$LOG_FILE.tmp" && mv "$LOG_FILE.tmp" "$LOG_FILE"

# ── Alert conditions ────────────────────────────────────────────────────

if [ "$NEW_STATE" -eq 1 ] && [ "$PREV_STATE" -eq 0 ]; then
    # SITE JUST WENT DOWN
    echo "🚨 *ZimRate is DOWN*
${TIMESTAMP}

*Issues detected:*
$(echo -e "$ERRORS")

I'll keep checking every 15 minutes and notify you when it recovers."
    exit 0

elif [ "$NEW_STATE" -eq 1 ] && [ "$PREV_STATE" -eq 1 ]; then
    # Still down — send periodic reminder every 2 hours
    # Use minute of day modulo to throttle: only alert on even 2-hour marks
    MINUTE_OF_DAY=$(( ($(date +%H) * 60 + $(date +%M)) ))
    if [ $((MINUTE_OF_DAY % 120)) -lt 15 ]; then
        echo "⚠️ *ZimRate still down*
${TIMESTAMP}

*Issues:*
$(echo -e "$ERRORS")

This is a periodic reminder. The site has been down."
    fi
    exit 0

elif [ "$NEW_STATE" -eq 0 ] && [ "$PREV_STATE" -eq 1 ]; then
    # SITE RECOVERED
    echo "✅ *ZimRate is back UP*
${TIMESTAMP}

All checks passing:
• Homepage: HTTP ${HTTP_CODE}
• Blog: HTTP ${BLOG_CODE}
• Java: running
• Caddy: ${CADDY_ALIVE}
• Service: ${SERVICE_STATUS}
• PostgreSQL: ${POSTGRES_STATUS}
• Disk ${DISK_PATH}: ${DISK_STATUS} used
• Memory available: ${MEM_STATUS}
• SSL expires in: ${SSL_DAYS} days"
    exit 0

else
    # Still healthy — stay silent
    exit 0
fi
