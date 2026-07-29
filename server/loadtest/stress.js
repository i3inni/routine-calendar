// k6 스트레스 테스트 — 부하를 계단식으로 크게 올려 "성능이 무너지는 지점(knee)"을 찾는다.
//   실행:  k6 run server/loadtest/stress.js
//   부하 거는 동안 Grafana(node CPU + 요청수)를 같이 보면 병목이 보인다.
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 50 },   // 워밍업
    { duration: '30s', target: 100 },
    { duration: '30s', target: 200 },
    { duration: '30s', target: 400 },  // 여기쯤 t3.small이 힘들어질 수 있음
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    // 스트레스라 임계 위반은 정보용(abortOnFail 안 함) — 어디서 깨지는지 보는 게 목적
    http_req_duration: ['p(95)<800'],
    http_req_failed:   ['rate<0.05'],
  },
};

const BASE = 'https://api.gatihae.com';

export default function () {
  const res = http.get(`${BASE}/actuator/health`);
  check(res, { 'status is 200': (r) => r.status === 200 });
  // sleep 없음 → 각 VU가 쉬지 않고 요청 = 같은 VU 수로도 RPS 훨씬 높아짐
}
