// k6 부하테스트 — 공개 엔드포인트에 점진적 부하를 주고 SLO(응답시간·에러율)를 검증한다.
//   실행:  k6 run server/loadtest/health.js
//   대상:  https://api.gatihae.com  (Nginx→앱 경로 그대로 = 실사용 경로)
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  // 부하 시나리오: 30s 동안 VU 20명까지 올리고 → 1분 유지 → 30s 동안 0으로 내림
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m',  target: 20 },
    { duration: '30s', target: 0 },
  ],
  // SLO(서비스 수준 목표): 하나라도 위반하면 k6가 실패로 종료
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% 요청이 500ms 이내
    http_req_failed:   ['rate<0.01'], // 실패율 1% 미만
  },
};

const BASE = 'https://api.gatihae.com';

export default function () {
  const res = http.get(`${BASE}/actuator/health`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'body is UP':    (r) => r.body && r.body.includes('UP'),
  });
  sleep(1); // 각 VU가 1초 간격으로 반복 (실제 사용자 흉내)
}
