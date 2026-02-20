#!/bin/bash
# ============================================================
#  untitles-api 부하 테스트 실행 스크립트
#  사용법: ./run.sh [smoke|load|stress|spike|multi|all]
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROM_URL="${PROM_URL:-http://localhost:9090/api/v1/write}"

run_test() {
  local test_name="$1"
  local script_path="$2"
  local test_id="${test_name}-$(date +%s)"

  echo ""
  echo "=========================================="
  echo " ${test_name} 시작 (testid: ${test_id})"
  echo "=========================================="

  K6_PROMETHEUS_RW_SERVER_URL="${PROM_URL}" \
  K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),min,max,avg,med" \
  K6_PROMETHEUS_RW_PUSH_INTERVAL=5s \
  k6 run \
    --out experimental-prometheus-rw \
    --tag testid="${test_id}" \
    "${script_path}"
}

case "${1:-help}" in
  smoke)
    run_test "smoke" "${SCRIPT_DIR}/smoke.js"
    ;;
  load)
    run_test "load" "${SCRIPT_DIR}/load.js"
    ;;
  stress)
    run_test "stress" "${SCRIPT_DIR}/stress.js"
    ;;
  spike)
    run_test "spike" "${SCRIPT_DIR}/spike.js"
    ;;
  multi)
    run_test "multi-user" "${SCRIPT_DIR}/multi-user.js"
    ;;
  all)
    run_test "smoke" "${SCRIPT_DIR}/smoke.js"
    echo "30초 대기..."
    sleep 30
    run_test "load" "${SCRIPT_DIR}/load.js"
    echo "30초 대기..."
    sleep 30
    run_test "stress" "${SCRIPT_DIR}/stress.js"
    echo "30초 대기..."
    sleep 30
    run_test "spike" "${SCRIPT_DIR}/spike.js"
    ;;
  *)
    echo "사용법: $0 {smoke|load|stress|spike|multi|all}"
    echo ""
    echo "  smoke   - VU 5, 1분 (정상 동작 확인)"
    echo "  load    - VU 30, 5분 (DAU 400 일상 부하)"
    echo "  stress  - VU 80, 5분 (피크 시간대)"
    echo "  spike   - VU 150, 6분 (한계 테스트)"
    echo "  multi   - 100명 개별 로그인, VU 80 (다중 사용자)"
    echo "  all     - smoke → load → stress → spike 순차 실행"
    exit 1
    ;;
esac
