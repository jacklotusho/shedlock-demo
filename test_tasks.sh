#!/usr/bin/env bash
# =============================================================================
# test_tasks.sh — Create a bunch of workflow requests for testing
#
# Usage:
#   chmod +x test_tasks.sh
#   ./test_tasks.sh                        # default: 10 requests, localhost:8080
#   ./test_tasks.sh 20                     # 20 requests
#   ./test_tasks.sh 20 localhost:9090      # custom host:port
#   ./test_tasks.sh 5 localhost:8080 true  # 5 requests, watch progress live
# =============================================================================
set -euo pipefail

# ── Config ────────────────────────────────────────────────────────────────────
COUNT="${1:-10}"
HOST="${2:-localhost:8080}"
WATCH="${3:-false}"
BASE_URL="http://${HOST}/api"

# ── Colors ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

# ── Helpers ───────────────────────────────────────────────────────────────────
check_server() {
    if ! curl -sf "${BASE_URL}/tasks/stats" > /dev/null 2>&1; then
        echo -e "${RED}ERROR: Cannot reach ${BASE_URL} — is the app running?${RESET}"
        echo -e "  Start with: ${CYAN}mvn spring-boot:run${RESET}"
        exit 1
    fi
}

print_stats() {
    local stats
    stats=$(curl -sf "${BASE_URL}/tasks/stats")
    local pending running completed failed
    pending=$(echo "$stats"  | grep -o '"pending":[0-9]*'   | cut -d: -f2)
    running=$(echo "$stats"  | grep -o '"running":[0-9]*'   | cut -d: -f2)
    completed=$(echo "$stats" | grep -o '"completed":[0-9]*' | cut -d: -f2)
    failed=$(echo "$stats"   | grep -o '"failed":[0-9]*'    | cut -d: -f2)
    echo -e "  📊 Stats: ${YELLOW}pending=${pending}${RESET}  ${CYAN}running=${running}${RESET}  ${GREEN}completed=${completed}${RESET}  ${RED}failed=${failed}${RESET}"
}

# ── Labels ────────────────────────────────────────────────────────────────────
LABELS=(
    "web-server-prod-01"
    "web-server-prod-02"
    "db-primary-east"
    "db-replica-west"
    "cache-node-redis-01"
    "worker-queue-processor"
    "api-gateway-blue"
    "api-gateway-green"
    "ml-inference-gpu-01"
    "ml-inference-gpu-02"
    "monitoring-prometheus"
    "log-aggregator-elk"
    "storage-nfs-node"
    "build-agent-ci-01"
    "build-agent-ci-02"
    "vpn-endpoint-us-east"
    "vpn-endpoint-eu-west"
    "load-balancer-haproxy"
    "jump-host-bastion"
    "backup-agent-nightly"
    "fyre-vm-test-node-01"
    "fyre-ocp-cluster-dev"
    "zpdt-instance-qa"
    "openstack-vm-staging"
    "ibmcloud-vpc-demo"
)

# ── Main ──────────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}╔══════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}║   ShedLock Demo — Task Generator         ║${RESET}"
echo -e "${BOLD}╚══════════════════════════════════════════╝${RESET}"
echo -e "  Target : ${CYAN}${BASE_URL}${RESET}"
echo -e "  Count  : ${BOLD}${COUNT}${RESET} requests"
echo ""

check_server

echo -e "${BOLD}Before:${RESET}"
print_stats
echo ""

# ── Create requests ───────────────────────────────────────────────────────────
echo -e "${BOLD}Creating ${COUNT} workflow requests...${RESET}"
echo ""

UUIDS=()
for i in $(seq 1 "$COUNT"); do
    # Pick label: use predefined list, cycle with suffix if more than list size
    LABEL_IDX=$(( (i - 1) % ${#LABELS[@]} ))
    BASE_LABEL="${LABELS[$LABEL_IDX]}"
    LABEL="${BASE_LABEL}-$(printf '%03d' $i)"

    RESPONSE=$(curl -sf -X POST "${BASE_URL}/requests" \
        -H "Content-Type: application/json" \
        -d "{\"label\": \"${LABEL}\"}")

    UUID=$(echo "$RESPONSE" | grep -o '"uuid":"[^"]*"' | cut -d'"' -f4)
    TASK_ID=$(echo "$RESPONSE" | grep -o '"taskId":[0-9]*' | cut -d: -f2)

    UUIDS+=("$UUID")
    echo -e "  [${i}/${COUNT}] ${GREEN}✓${RESET} ${LABEL}  →  taskId=${BOLD}${TASK_ID}${RESET}  uuid=${CYAN}${UUID:0:8}...${RESET}"
done

echo ""
echo -e "${GREEN}${BOLD}✅ ${COUNT} requests created.${RESET}"
echo ""

# ── Immediate stats ───────────────────────────────────────────────────────────
echo -e "${BOLD}Immediately after creation:${RESET}"
print_stats
echo ""

# ── Watch mode ────────────────────────────────────────────────────────────────
if [ "$WATCH" = "true" ]; then
    echo -e "${BOLD}Watching progress (Ctrl+C to stop)...${RESET}"
    echo ""
    PREV_COMPLETED=-1
    while true; do
        STATS=$(curl -sf "${BASE_URL}/tasks/stats")
        COMPLETED=$(echo "$STATS" | grep -o '"completed":[0-9]*' | cut -d: -f2)
        PENDING=$(echo "$STATS"   | grep -o '"pending":[0-9]*'   | cut -d: -f2)
        RUNNING=$(echo "$STATS"   | grep -o '"running":[0-9]*'   | cut -d: -f2)
        FAILED=$(echo "$STATS"    | grep -o '"failed":[0-9]*'    | cut -d: -f2)

        TOTAL_EXPECTED=$(( COUNT * 4 ))   # 4 steps per workflow

        if [ "$COMPLETED" != "$PREV_COMPLETED" ]; then
            TS=$(date '+%H:%M:%S')
            BAR_FILL=$(( COMPLETED * 30 / (TOTAL_EXPECTED > 0 ? TOTAL_EXPECTED : 1) ))
            BAR_EMPTY=$(( 30 - BAR_FILL ))
            BAR="[$(printf '█%.0s' $(seq 1 $BAR_FILL 2>/dev/null || true))$(printf '░%.0s' $(seq 1 $BAR_EMPTY 2>/dev/null || true))]"
            echo -e "  ${TS}  ${BAR} ${GREEN}${COMPLETED}/${TOTAL_EXPECTED}${RESET} done  ${YELLOW}pending=${PENDING}${RESET}  ${CYAN}running=${RUNNING}${RESET}  ${RED}failed=${FAILED}${RESET}"
            PREV_COMPLETED=$COMPLETED
        fi

        # Stop when all expected steps done or nothing left to process
        if [ "$PENDING" = "0" ] && [ "$RUNNING" = "0" ]; then
            echo ""
            echo -e "${GREEN}${BOLD}✅ All tasks processed!${RESET}"
            echo ""
            print_stats
            break
        fi
        sleep 2
    done
else
    echo -e "${YELLOW}Tip:${RESET} Run with watch mode to follow progress:"
    echo -e "  ${CYAN}./test_tasks.sh ${COUNT} ${HOST} true${RESET}"
    echo ""
    echo -e "${YELLOW}Tip:${RESET} Check a specific workflow:"
    if [ ${#UUIDS[@]} -gt 0 ]; then
        echo -e "  ${CYAN}curl ${BASE_URL}/requests/${UUIDS[0]}${RESET}"
    fi
    echo ""
    echo -e "${YELLOW}Tip:${RESET} Poll stats manually:"
    echo -e "  ${CYAN}curl ${BASE_URL}/tasks/stats${RESET}"
    echo ""
    echo -e "${YELLOW}Tip:${RESET} Watch in a loop:"
    echo -e "  ${CYAN}watch -n2 'curl -s ${BASE_URL}/tasks/stats'${RESET}"
fi
