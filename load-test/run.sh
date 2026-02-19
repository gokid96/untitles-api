#!/bin/bash
# ============================================================
#  k6 실행 스크립트 (Prometheus Remote Write)
# ============================================================

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PROMETHEUS_URL="http://localhost:9090/api/v1/write"
TREND_STATS="p(95),p(99),min,max,avg"

run_test() {
  local test_name=$1
  local script=$2
  local testid="${test_name}-$(date +%s)"

  echo "=========================================="
  echo " ${test_name} 시작 (testid: ${testid})"
  echo "=========================================="

  K6_PROMETHEUS_RW_SERVER_URL=${PROMETHEUS_URL} \
  K6_PROMETHEUS_RW_TREND_STATS=${TREND_STATS} \
  K6_PROMETHEUS_RW_STALE_MARKERS=true \
  k6 run -o experimental-prometheus-rw \
    --tag testid=${testid} \
    ${script}

  echo ""
  echo "✅ ${test_name} 완료"
  echo "Grafana에서 testid: ${testid} 로 필터링하세요"
  echo ""
}

case "$1" in
  smoke)
    run_test "smoke" "${SCRIPT_DIR}/smoke.js"
    ;;
  load)
    run_test "load" "${SCRIPT_DIR}/load.js"
    ;;
  stress)
    run_test "stress" "${SCRIPT_DIR}/stress.js"
    ;;
  all)
    run_test "smoke" "${SCRIPT_DIR}/smoke.js"
    echo "30초 대기 후 Load Test 시작..."
    sleep 30
    run_test "load" "${SCRIPT_DIR}/load.js"
    echo "30초 대기 후 Stress Test 시작..."
    sleep 30
    run_test "stress" "${SCRIPT_DIR}/stress.js"
    ;;
  *)
    echo "사용법: $0 {smoke|load|stress|all}"
    exit 1
    ;;
esac
