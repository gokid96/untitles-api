# k6 부하 테스트 가이드 - untitles-api

## 사전 준비

### 1. k6 설치
```bash
# macOS
brew install k6

# Windows (choco)
choco install k6

# Windows (winget)
winget install k6
```

### 2. 테스트 계정 준비
EC2 서버에 테스트용 계정을 미리 만들어주세요:
- loginId: `k6testuser`
- password: `testPassword123!`

그리고 해당 계정으로 워크스페이스를 1개 이상 만들어두세요.

### 3. 설정 변경
`k6/config.js`에서 `BASE_URL`을 실제 EC2 주소로 변경하거나,
실행 시 환경변수로 전달합니다.

---

## 테스트 실행 순서

### Step 1: Smoke Test (먼저!)
서버가 정상 동작하는지 확인합니다.
```bash
k6 run k6/01-smoke.js -e BASE_URL=https://your-api.com
```

### Step 2: Load Test
예상 트래픽에서의 성능을 확인합니다.
```bash
k6 run k6/02-load.js -e BASE_URL=https://your-api.com
```

### Step 3: Stress Test
시스템 한계를 찾습니다.
```bash
k6 run k6/03-stress.js -e BASE_URL=https://your-api.com
```

### Step 4: Spike Test
갑작스러운 트래픽 급증 대응을 확인합니다.
```bash
k6 run k6/04-spike.js -e BASE_URL=https://your-api.com
```

### Step 5: Public API Test
공개 페이지 성능을 확인합니다.
```bash
k6 run k6/05-public-api.js -e BASE_URL=https://your-api.com -e SLUG=your-slug
```

---

## 결과 보는 법

k6 실행 후 출력되는 주요 지표:

| 지표 | 의미 | 기준 |
|------|------|------|
| `http_req_duration` | 요청~응답 시간 | p(95) < 500ms |
| `http_req_failed` | 에러율 | < 1% |
| `http_reqs` | 초당 처리량(RPS) | 높을수록 좋음 |
| `vus` | 동시 가상 유저 수 | 테스트 설정값 |
| `iterations` | 총 실행 횟수 | 높을수록 좋음 |

---

## EC2 + RDS 환경에서 주의할 점

### Rate Limit 주의
이 프로젝트에는 Rate Limit이 설정되어 있습니다:
- 일반 API: **분당 60회** (IP 기반)
- 로그인: **분당 10회**
- 이메일: **시간당 5회**

k6는 같은 IP에서 요청하므로, VU가 많으면 Rate Limit에 걸릴 수 있습니다.

**해결 방법:**
1. 테스트 시 Rate Limit을 일시적으로 완화
2. 또는 `RateLimitFilter`에서 특정 IP/헤더를 화이트리스트 처리

### 세션 기반 인증
이 프로젝트는 JWT가 아닌 세션 기반이므로:
- 매 VU 반복마다 로그인이 필요 (세션 쿠키 획득)
- `http.cookieJar()`로 쿠키를 관리합니다
- 동시 세션이 많아지면 서버 메모리에 영향

### RDS 모니터링
테스트 중 AWS 콘솔에서 확인할 것:
- **RDS → Performance Insights**: 슬로우 쿼리 확인
- **RDS → Monitoring**: CPU, 커넥션 수, 읽기/쓰기 IOPS
- **EC2 → Monitoring**: CPU, 네트워크, 메모리(CloudWatch Agent 필요)

### 테스트 데이터 정리
Load/Stress 테스트 후 생성된 테스트 게시글들이 남을 수 있습니다.
스크립트에서 생성 후 삭제하도록 되어 있지만, 에러로 삭제 안 된 것들은
수동으로 정리해주세요.

---

## Prometheus + Grafana 연동 (이미 설정됨!)

application.yml에 이미 Actuator + Prometheus 설정이 있으므로:

```bash
# Prometheus 메트릭 확인
curl https://your-api.com/actuator/prometheus
```

k6 결과를 Prometheus로 보내려면:
```bash
k6 run \
  -o experimental-prometheus-rw \
  -e K6_PROMETHEUS_RW_SERVER_URL=http://your-prometheus:9090/api/v1/write \
  k6/02-load.js
```

---

## EC2 인스턴스별 예상 한계

| 인스턴스 | vCPU | 메모리 | 예상 동시 유저 |
|----------|------|--------|---------------|
| t3.micro | 2 | 1GB | ~20-30 |
| t3.small | 2 | 2GB | ~50-80 |
| t3.medium | 2 | 4GB | ~100-150 |
| t3.large | 2 | 8GB | ~200-300 |

※ 실제로는 RDS 스펙, JVM 힙 설정, 쿼리 복잡도에 따라 크게 달라집니다.
   Stress Test로 직접 확인하는 것이 가장 정확합니다.
