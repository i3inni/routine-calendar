// k6 — 인증이 필요한 /me/friends 부하테스트 (캐시 효과 비교용).
//   토큰 주입:  k6 run -e TOKEN=<accessToken> server/loadtest/friends.js
//   대상 변경:  k6 run -e TOKEN=... -e BASE=https://api.gatihae.com server/loadtest/friends.js
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 50,
  duration: '20s',
};

const BASE = __ENV.BASE || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN;

export default function () {
  const res = http.get(`${BASE}/me/friends`, {
    headers: { Authorization: `Bearer ${TOKEN}` },
  });
  check(res, { 'status is 200': (r) => r.status === 200 });
}
