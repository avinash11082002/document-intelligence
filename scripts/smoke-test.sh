#!/usr/bin/env bash
# ==============================================================================
# Multi-Microservice Smoke Test — AI-Powered Document Intelligence Pipeline
# Tests the distributed flow across docint-api (8080), docint-worker (8081),
# and docint-query (8082).
# ==============================================================================
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
WORKER_URL="${WORKER_URL:-http://localhost:8081}"
QUERY_URL="${QUERY_URL:-http://localhost:8082}"

OWNER_ID="smoke-user-$(date +%s)"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log()  { echo -e "${GREEN}[✓]${NC} $1"; }
info() { echo -e "${BLUE}[i]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
fail() { echo -e "${RED}[✗]${NC} $1"; exit 1; }

echo "=================================================================="
echo " Document Intelligence Pipeline — Microservices Smoke Test"
echo " API:    $API_URL"
echo " Worker: $WORKER_URL"
echo " Query:  $QUERY_URL"
echo "=================================================================="
echo ""

# --- 1. Health Checks Across All Microservices ---
info "1. Verifying Microservice Health Endpoints..."
for SVC in "docint-api:$API_URL" "docint-worker:$WORKER_URL" "docint-query:$QUERY_URL"; do
  NAME="${SVC%%:*}"
  URL="${SVC#*:}"
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$URL/actuator/health" || echo "000")
  if [ "$STATUS" = "200" ]; then
    log "  $NAME is UP (HTTP 200)"
  else
    fail "  $NAME is DOWN (HTTP $STATUS at $URL/actuator/health)"
  fi
done

# --- 2. Prepare Sample Document ---
info "2. Generating sample commercial invoice document..."
SAMPLE_FILE="/tmp/docint_invoice_sample.txt"
cat > "$SAMPLE_FILE" << 'EOF'
COMMERCIAL INVOICE
Invoice Number: INV-2025-9842
Date of Issue: October 24, 2025
Supplier: Apex Cloud Technologies Inc.
Supplier Address: 500 Silicon Way, San Francisco, CA 94105
Tax ID: US-94-8271649

Client / Buyer:
  Global Media Group LLC
  789 Broadway Ave, New York, NY 10003
  Contact: Sarah Jenkins (VP Operations)

Billable Line Items:
  1. Distributed Kafka Stream Processing Cluster (Dedicated): $4,500.00
  2. Elasticsearch Hybrid Search Infrastructure (Managed): $2,200.00
  3. AI Pipeline Integration & Orchestration Service: $3,800.00

Financial Breakdown:
  Subtotal Amount: $10,500.00
  State & Local Tax (8.5%): $892.50
  Total Amount Payable: $11,392.50

Payment Terms & Conditions:
  Payment terms are Net 30 days.
  Full balance of $11,392.50 is due by November 23, 2025.
  Late payments are subject to a 1.5% monthly service charge.
EOF
log "Sample invoice generated at $SAMPLE_FILE"

# --- 3. Test Ingestion via docint-api (8080) ---
echo ""
info "3. Testing Document Ingestion (POST $API_URL/api/documents)..."
UPLOAD_RESP=$(curl -s -X POST "$API_URL/api/documents" \
  -H "X-Correlation-Id: smoke-corr-101" \
  -F "file=@$SAMPLE_FILE;type=application/pdf" \
  -F "ownerId=$OWNER_ID")

DOC_ID=$(echo "$UPLOAD_RESP" | jq -r '.id')
DOC_STATUS=$(echo "$UPLOAD_RESP" | jq -r '.status')

if [ "$DOC_STATUS" = "UPLOADED" ] && [ "$DOC_ID" != "null" ]; then
  log "Document uploaded successfully: ID = $DOC_ID (Status: $DOC_STATUS)"
else
  fail "Upload failed: $UPLOAD_RESP"
fi

# --- 4. Test Idempotency (Content-Hash Deduplication) ---
echo ""
info "4. Testing Idempotent Deduplication (Re-upload identical content)..."
DEDUP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/api/documents" \
  -F "file=@$SAMPLE_FILE;type=application/pdf" \
  -F "ownerId=$OWNER_ID")

if [ "$DEDUP_CODE" = "409" ]; then
  log "Content-hash deduplication verified (HTTP 409 Conflict returned)"
else
  warn "Expected HTTP 409 Conflict, received HTTP $DEDUP_CODE"
fi

# --- 5. Await Async Processing in docint-worker (8081) ---
echo ""
info "5. Monitoring asynchronous Kafka processing pipeline..."
MAX_WAIT=90
WAITED=0
INDEXED=false

while [ $WAITED -lt $MAX_WAIT ]; do
  STATUS_RESP=$(curl -s "$API_URL/api/documents/$DOC_ID" -H "X-Owner-Id: $OWNER_ID")
  CURRENT_STATUS=$(echo "$STATUS_RESP" | jq -r '.status // empty')

  if [ "$CURRENT_STATUS" = "INDEXED" ]; then
    log "Document state transitioned to INDEXED (in ${WAITED}s)"
    DOC_TYPE=$(echo "$STATUS_RESP" | jq -r '.documentType // "unknown"')
    CHUNKS=$(echo "$STATUS_RESP" | jq -r '.chunkCount // 0')
    log "  LLM Classification: $DOC_TYPE"
    log "  Elasticsearch Chunks: $CHUNKS"
    INDEXED=true
    break
  elif [ "$CURRENT_STATUS" = "FAILED" ]; then
    REASON=$(echo "$STATUS_RESP" | jq -r '.failureReason // "unspecified"')
    fail "Worker processing failed with status FAILED: $REASON"
  fi

  echo -ne "  Current status: ${YELLOW}${CURRENT_STATUS:-CONNECTING}${NC} (waited ${WAITED}s)...\r"
  sleep 4
  WAITED=$((WAITED + 4))
done
echo ""

if [ "$INDEXED" = false ]; then
  fail "Timed out waiting for document to be INDEXED by docint-worker"
fi

# --- 6. Test RAG Q&A via docint-query (8082) ---
echo ""
info "6. Executing RAG Natural Language Query (POST $QUERY_URL/api/query)..."
QUERY_PAYLOAD=$(cat << EOF
{
  "ownerId": "$OWNER_ID",
  "documentId": "$DOC_ID",
  "question": "What is the total payable amount on this invoice and when is it due?"
}
EOF
)

QUERY_RESP=$(curl -s -X POST "$QUERY_URL/api/query" \
  -H "Content-Type: application/json" \
  -d "$QUERY_PAYLOAD")

ANSWER=$(echo "$QUERY_RESP" | jq -r '.answer // empty')
CACHED=$(echo "$QUERY_RESP" | jq -r '.cached // false')
PROVIDER=$(echo "$QUERY_RESP" | jq -r '.llmProvider // "unknown"')
LATENCY=$(echo "$QUERY_RESP" | jq -r '.latencyMs // 0')
CHUNK_COUNT=$(echo "$QUERY_RESP" | jq -r '.sourceChunks | length')

if [ -n "$ANSWER" ] && [ "$CHUNK_COUNT" -gt 0 ]; then
  log "RAG query answered successfully ($LATENCY ms, LLM: $PROVIDER, Sources: $CHUNK_COUNT)"
  echo -e "  ${YELLOW}Answer:${NC} $ANSWER"
else
  fail "RAG query failed or returned no answer: $QUERY_RESP"
fi

# --- 7. Test Distributed Redis Cache Hit ---
echo ""
info "7. Testing Redis Cache Hit on repeated identical query..."
CACHE_RESP=$(curl -s -X POST "$QUERY_URL/api/query" \
  -H "Content-Type: application/json" \
  -d "$QUERY_PAYLOAD")

IS_CACHED=$(echo "$CACHE_RESP" | jq -r '.cached')
CACHE_LATENCY=$(echo "$CACHE_RESP" | jq -r '.latencyMs')

if [ "$IS_CACHED" = "true" ]; then
  log "Redis cache HIT verified ($CACHE_LATENCY ms)"
else
  warn "Cache hit expected true, got $IS_CACHED ($CACHE_LATENCY ms)"
fi

# --- Cleanup ---
rm -f "$SAMPLE_FILE"

echo ""
echo "=================================================================="
echo -e "${GREEN} ALL MICROSERVICES SMOKE TESTS COMPLETED SUCCESSFULLY! ${NC}"
echo "=================================================================="
