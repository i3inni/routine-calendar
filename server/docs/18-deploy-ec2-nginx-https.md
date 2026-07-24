# 18 — 배포: EC2 + Docker + Nginx + HTTPS

> [← 17 피드백](17-feedback.md)

PaaS(Railway/KoDeploy) 없이 **AWS EC2에 직접 배포**하고, 앞단에 **Nginx 리버스 프록시**와 **Let's Encrypt HTTPS**를 붙인 구성을 정리한다. "서버를 인터넷에 안전하게 띄운다"의 전체 그림.

---

## 전체 아키텍처

```
브라우저 / iOS 앱
      │  https://api.gatihae.com        (443, TLS)
      ▼
  ┌────────────────── EC2 (Ubuntu 24.04, t3.small) ──────────────────┐
  │  Nginx (호스트에 상주)                                             │
  │    - 443에서 TLS 종단 (Let's Encrypt 인증서)                       │
  │    - 80 → 443 리다이렉트                                          │
  │    - 요청을 뒤로 프록시  ─────────┐                               │
  │                                    ▼                              │
  │  Docker (docker-compose.prod.yml)                                 │
  │    ┌── server 컨테이너 (Spring Boot) 127.0.0.1:8080 ──┐           │
  │    │      · 외부 직접 접근 불가(루프백 바인딩)          │           │
  │    └────────────────┬────────────────────────────────┘           │
  │                     ▼                                             │
  │    ┌── postgres 컨테이너 (5432, 컨테이너 네트워크 내부) ──┐        │
  │    └─────────────────────────────────────────────────────┘        │
  └───────────────────────────────────────────────────────────────────┘
```

**핵심 아이디어**: Nginx는 호스트에 **안정적인 현관문**으로 상주하고, 그 뒤의 앱 컨테이너는 언제든 교체(재배포)된다. 앱은 `127.0.0.1:8080` 에만 열려 있어 **오직 Nginx만** 접근할 수 있다.

---

## 1. EC2 준비

| 항목 | 값 | 이유 |
|---|---|---|
| OS | Ubuntu Server 24.04 LTS | Docker 돌릴 표준 리눅스 |
| 인스턴스 | t3.small (vCPU2/RAM2G) | 모니터링까지 얹으면 micro(1G)는 부족 |
| 리전 | ap-northeast-2 (서울) | 사용자 지연 최소 |
| 루트 EBS | 30GB gp3 | Docker 이미지·DB 볼륨 여유 |
| swap | 2GB (`/swapfile`) | 빌드 시 OOM 방지 (RAM 2G 보완) |
| Elastic IP | 13.124.7.223 | **고정 공인 IP** — 재부팅해도 안 바뀜 |

**보안 그룹(방화벽) 인바운드**: `22(SSH)`, `80(HTTP)`, `443(HTTPS)` 만 개방.
- `8080`은 열지 않는다 → 앱은 Nginx를 통해서만 노출.

```bash
# 접속
ssh -i ~/.ssh/routine-key.pem ubuntu@13.124.7.223

# swap 2GB
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Docker 설치
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu   # sudo 없이 docker (재접속 후 적용)
```

> 💡 **비용**: t3.small은 프리티어가 아니다(켜둔 시간당 과금). 공부 끝나면 인스턴스 **Stop(중지)** 하면 컴퓨팅 과금이 멈춘다. Elastic IP 덕에 껐다 켜도 IP는 그대로. 완전 삭제는 **Terminate + Elastic IP 릴리스**.

---

## 2. Docker로 앱 + DB 기동

`server/docker-compose.prod.yml` (개발용 `docker-compose.yml` 과 분리):
- 비밀번호·시크릿을 하드코딩하지 않고 **`.env` 에서 주입**(커밋 금지).
- `DEV_LOGIN_ENABLED=false` (개발용 무인증 로그인 차단).
- postgres 포트를 호스트로 노출하지 않음(컨테이너 네트워크 내부 전용).
- server 컨테이너는 **`127.0.0.1:8080` 에만 바인딩** → 외부에서 직접 못 붙는다.

```bash
cd ~/routine-calendar/server

# .env 생성 (예시 — 값은 무작위로)
cat > .env <<EOF
DB_NAME=routine_calendar
DB_USERNAME=routine
DB_PASSWORD=$(openssl rand -hex 16)
JWT_SECRET=$(openssl rand -hex 32)
JWT_ACCESS_VALIDITY=3600
JWT_REFRESH_VALIDITY=2592000
DEV_LOGIN_ENABLED=false
APNS_ENABLED=false
EOF

docker compose -f docker-compose.prod.yml up -d
curl -s http://localhost:8080/actuator/health   # {"status":"UP"}
```

> DB 스키마는 **Flyway**가 컨테이너 기동 시 자동 마이그레이션한다(JPA는 `validate`만).

---

## 3. Nginx 리버스 프록시

**리버스 프록시가 왜 필요한가?**
- 앱을 80/443에 직접 두는 대신 앞에 Nginx를 세우면: ① 여러 앱/도메인 라우팅 ② TLS 종단을 한 곳에서 ③ 앱 재배포와 무관하게 현관 유지 ④ 요청 로깅·헤더 정리.

호스트에 Nginx 설치 후 `/etc/nginx/sites-available/api.gatihae.com`:

```nginx
server {
    listen 80;
    server_name api.gatihae.com;

    location / {
        proxy_pass http://127.0.0.1:8080;          # 뒤의 앱 컨테이너로 전달
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme; # 앱이 원래 스킴(https)을 알게
    }
}
```

```bash
sudo apt install -y nginx
sudo ln -s /etc/nginx/sites-available/api.gatihae.com /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

**DNS**: Cloudflare에서 A 레코드 `api` → `13.124.7.223` (프록시 끔 = 회색 구름).

---

## 4. HTTPS (Let's Encrypt / certbot)

무료 인증서를 발급받고 Nginx에 자동 적용:

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d api.gatihae.com
```

certbot이 하는 일:
1. Let's Encrypt에 도메인 소유 증명(HTTP-01 챌린지, 80포트 사용).
2. 인증서 발급 후 **Nginx 설정에 443 + TLS 블록을 자동 추가**.
3. **HTTP→HTTPS 301 리다이렉트** 설정.
4. **자동 갱신 타이머**(systemd) 등록 → 90일 만료 전 자동 재발급.

```bash
curl -s https://api.gatihae.com/actuator/health   # {"status":"UP"}
```

---

## 5. 마무리 — 8080 외부 차단

Nginx가 앞에 섰으니 앱 포트는 밖에서 막는다:
- compose에서 `ports: "127.0.0.1:8080:8080"` (루프백 바인딩).
- 보안 그룹에서 8080 규칙 삭제.

결과: 외부 접속은 **오직 `https://api.gatihae.com`** 뿐. `curl http://<IP>:8080` 은 타임아웃.

---

## 운영 치트시트

```bash
# 상태 / 로그
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f server

# 재배포(코드 갱신 후)
git pull && docker compose -f docker-compose.prod.yml up -d

# 인증서 갱신 테스트
sudo certbot renew --dry-run
```

---

## 다음 단계

- **CI/CD (GitHub Actions → ECR → EC2 자동배포)**: `.github/workflows/deploy.yml` 참고. main push 시 test→이미지 빌드→ECR push→EC2 SSH 배포. compose는 `image: ${SERVER_IMAGE}` 로 ECR 이미지를 pull 한다.
- **관측성 (Prometheus + Grafana)**: `server/monitoring/` 참고.
