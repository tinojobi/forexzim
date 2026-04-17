#!/bin/bash
# Weekly ZimRate Blog Pipeline
# Triggers Nova to research and draft a blog post
# Runs every Monday at 8:00 AM CAT (06:00 UTC)

LOG="/opt/forexzim/blog/pipeline.log"
DATE=$(date +%Y-%m-%d)

echo "[$DATE] Blog pipeline triggered" >> "$LOG"
