#!/usr/bin/env bash
# Manual endpoint tests for the Packing & Moving Survey API.
# Run the server first: uv run uvicorn main:app --reload
# Usage: bash test_endpoints.sh

BASE_URL="http://localhost:8000"

# ─── colours ────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

header() { echo -e "\n${BLUE}══════════════════════════════════════${NC}"; echo -e "${GREEN}$1${NC}"; echo -e "${BLUE}══════════════════════════════════════${NC}"; }

# ─── 1. Upload images ────────────────────────────────────────────────────────
header "POST /upload — upload one or more images"

# Replace the paths below with real image files on your machine.
# Any .jpg / .png / .webp will do.
IMAGE_1="test_image.jpg"
IMAGE_2="test_image2.jpg"

# Single image upload
echo "▶ Single image upload"
UPLOAD_RESPONSE=$(curl -s -X POST "$BASE_URL/upload" \
  -F "files=@$IMAGE_1;type=image/jpeg")

echo "$UPLOAD_RESPONSE" | python3 -m json.tool
SESSION_ID=$(echo "$UPLOAD_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['session_id'])" 2>/dev/null)
echo "→ session_id: $SESSION_ID"

# Multi-image upload
echo ""
echo "▶ Multi-image upload"
curl -s -X POST "$BASE_URL/upload" \
  -F "files=@$IMAGE_1;type=image/jpeg" \
  -F "files=@$IMAGE_2;type=image/jpeg" \
  | python3 -m json.tool

# ─── 2. Error: unsupported file type ────────────────────────────────────────
header "POST /upload — unsupported MIME type (expect 415)"
curl -s -X POST "$BASE_URL/upload" \
  -F "files=@test_endpoints.sh;type=text/plain" \
  | python3 -m json.tool

# ─── 3. Check processing status ─────────────────────────────────────────────
header "GET /processing/{id} — poll status for the session above"

if [ -z "$SESSION_ID" ]; then
  echo "No session ID captured — set SESSION_ID manually and re-run the block below."
  SESSION_ID="REPLACE-WITH-REAL-UUID"
fi

echo "▶ Fetching status for session: $SESSION_ID"
curl -s "$BASE_URL/processing/$SESSION_ID" | python3 -m json.tool

# ─── 4. Poll until completed ─────────────────────────────────────────────────
header "GET /processing/{id} — wait for AI analysis to finish (up to 60 s)"
for i in $(seq 1 12); do
  STATUS=$(curl -s "$BASE_URL/processing/$SESSION_ID" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['status'])" 2>/dev/null)
  echo "  attempt $i — status: $STATUS"
  if [[ "$STATUS" == "completed" || "$STATUS" == "failed" ]]; then
    echo "  → Done. Final result:"
    curl -s "$BASE_URL/processing/$SESSION_ID" | python3 -m json.tool
    break
  fi
  sleep 5
done

# ─── 5. List all surveys ─────────────────────────────────────────────────────
header "GET /processing — list all survey sessions"
curl -s "$BASE_URL/processing" | python3 -m json.tool

# ─── 6. 404 for unknown session ──────────────────────────────────────────────
header "GET /processing/{id} — unknown UUID (expect 404)"
curl -s "$BASE_URL/processing/00000000-0000-0000-0000-000000000000" \
  | python3 -m json.tool

echo -e "\n${GREEN}Done.${NC}"
