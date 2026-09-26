#!/usr/bin/env bash
# ==============================================================================
# Microservices Load Test — AI-Powered Document Intelligence Pipeline
# Benchmarks docint-api (8080) ingestion and docint-query (8082) RAG execution.
# Usage: ./scripts/load-test.sh [num_requests]
# ==============================================================================
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
QUERY_URL="${QUERY_URL:-http://localhost:8082}"
NUM_REQUESTS="${1:-5}"
OWNER_ID="loadtest-owner-$(date +%s)"
RESULTS_CSV="/tmp/docint_loadtest_metrics.csv"

echo "=================================================================="
echo " Document Intelligence Pipeline — Load & Latency Benchmark"
echo " API URL:   $API_URL"
echo " Query URL: $QUERY_URL"
echo " Requests:  $NUM_REQUESTS"
echo "=================================================================="
echo ""

echo "timestamp,service,endpoint,http_status,latency_ms" > "$RESULTS_CSV"

SAMPLE_BASE="/tmp/docint_load_base.txt"
cat > "$SAMPLE_BASE" << 'EOF'
Executive Summary & Technical Architecture
The Document Intelligence Platform implements an asynchronous event-driven
microservices architecture leveraging Spring Boot 3, Apache Kafka in KRaft mode,
PostgreSQL for ACID persistence, Redis for sub-millisecond caching, and Elasticsearch 8
for approximate cosine similarity k-NN vector search combined with BM25 keyword matching.

Resilience is guaranteed via Resilience4j circuit breakers providing automated fallback
from Google Gemini to Groq ultra-low-latency LPU infrastructure.
EOF

# --- Phase 1: Ingestion Load ---
echo "--- Phase 1: Document Upload Ingestion (docint-api:8080) ---"
UPLOAD_TOTAL_MS=0
UPLOAD_OK=0

for i in $(seq 1 "$NUM_REQUESTS"); do
  CUR_FILE="/tmp/docint_load_$i.txt"
  cp "$SAMPLE_BASE" "$CUR_FILE"
  echo "Document serial batch token: $i - $(date +%s%N)" >> "$CUR_FILE"

  T_START=$(date +%s%N)
  STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/api/documents" \
    -F "file=@$CUR_FILE;type=application/pdf" \
    -F "ownerId=$OWNER_ID")
  T_END=$(date +%s%N)
  LATENCY=$(( (T_END - T_START) / 1000000 ))

  echo "$(date -Iseconds),docint-api,/api/documents,$STATUS_CODE,$LATENCY" >> "$RESULTS_CSV"
  if [ "$STATUS_CODE" = "201" ]; then
    UPLOAD_OK=$((UPLOAD_OK + 1))
  fi
  UPLOAD_TOTAL_MS=$((UPLOAD_TOTAL_MS + LATENCY))
  printf "  [Upload %02d/%02d] HTTP %s (%d ms)\n" "$i" "$NUM_REQUESTS" "$STATUS_CODE" "$LATENCY"

  rm -f "$CUR_FILE"
  sleep 0.2
done

AVG_UPLOAD_MS=$((UPLOAD_TOTAL_MS / NUM_REQUESTS))
echo "Upload Benchmark: $UPLOAD_OK/$NUM_REQUESTS success | Avg Latency: ${AVG_UPLOAD_MS}ms"
echo ""

# Allow worker to process batch
echo "Waiting 12 seconds for Kafka worker ingestion and indexing..."
sleep 12
echo ""

# --- Phase 2: RAG Query Load ---
echo "--- Phase 2: RAG Natural Language Query (docint-query:8082) ---"
QUERY_TOTAL_MS=0
QUERY_OK=0

QUESTIONS=(
  "What databases and search engines are utilized?"
  "How is resilience handled across LLM providers?"
  "What is the underlying architecture of the platform?"
)

for i in $(seq 1 "$NUM_REQUESTS"); do
  Q_IDX=$(( (i - 1) % ${#QUESTIONS[@]} ))
  Q_TEXT="${QUESTIONS[$Q_IDX]}"

  T_START=$(date +%s%N)
  STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$QUERY_URL/api/query" \
    -H "Content-Type: application/json" \
    -d "{\"ownerId\": \"$OWNER_ID\", \"question\": \"$Q_TEXT\"}")
  T_END=$(date +%s%N)
  LATENCY=$(( (T_END - T_START) / 1000000 ))

  echo "$(date -Iseconds),docint-query,/api/query,$STATUS_CODE,$LATENCY" >> "$RESULTS_CSV"
  if [ "$STATUS_CODE" = "200" ]; then
    QUERY_OK=$((QUERY_OK + 1))
  fi
  QUERY_TOTAL_MS=$((QUERY_TOTAL_MS + LATENCY))
  printf "  [Query  %02d/%02d] HTTP %s (%d ms) - '%s'\n" "$i" "$NUM_REQUESTS" "$STATUS_CODE" "$LATENCY" "$Q_TEXT"

  sleep 0.5
done

AVG_QUERY_MS=$((QUERY_TOTAL_MS / NUM_REQUESTS))
echo ""
echo "=================================================================="
echo " Load Test Summary"
echo "=================================================================="
echo " Uploads: $UPLOAD_OK / $NUM_REQUESTS successful (Avg: ${AVG_UPLOAD_MS} ms)"
echo " Queries: $QUERY_OK / $NUM_REQUESTS successful (Avg: ${AVG_QUERY_MS} ms)"
echo " Detailed log: $RESULTS_CSV"
echo "=================================================================="

rm -f "$SAMPLE_BASE"
