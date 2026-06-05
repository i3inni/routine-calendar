package com.routinecalendar.server.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개인정보 처리방침 페이지. App Store 제출 시 필요한 공개 URL(/privacy).
 */
@RestController
public class PrivacyController {

    @GetMapping(value = "/privacy", produces = MediaType.TEXT_HTML_VALUE)
    public String privacy() {
        return """
                <!doctype html>
                <html lang="ko"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>같이해 개인정보 처리방침</title>
                <style>
                  body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;max-width:720px;margin:0 auto;
                       padding:40px 20px;color:#222;line-height:1.7}
                  h1{font-size:24px} h2{font-size:18px;margin-top:32px}
                  p,li{font-size:15px} .muted{color:#888;font-size:13px}
                  table{border-collapse:collapse;width:100%;margin:12px 0}
                  th,td{border:1px solid #ddd;padding:8px;text-align:left;font-size:14px}
                </style></head>
                <body>
                  <h1>같이해 개인정보 처리방침</h1>
                  <p class="muted">최종 업데이트: 2026-06-05</p>

                  <p>‘같이해’(이하 “서비스”)는 친구와 함께 루틴을 관리하는 앱입니다.
                     서비스 제공에 필요한 최소한의 정보만 수집하며, 광고·추적 목적의 데이터는 수집하지 않습니다.</p>

                  <h2>1. 수집하는 정보와 목적</h2>
                  <table>
                    <tr><th>항목</th><th>목적</th></tr>
                    <tr><td>카카오 로그인 정보(회원번호, 닉네임, 프로필 이미지)</td><td>로그인, 친구 목록에 표시</td></tr>
                    <tr><td>Apple 로그인 정보(사용자 식별자, 이름)</td><td>로그인, 친구 목록에 표시</td></tr>
                    <tr><td>기기 푸시 토큰</td><td>친구의 ‘콕 찌르기’·요청 알림 발송</td></tr>
                    <tr><td>루틴 요약(완료/남은 루틴 이름, 연속일수)</td><td>친구와 진행 상황 공유</td></tr>
                    <tr><td>친구 관계, 콕 기록</td><td>친구 기능 제공</td></tr>
                  </table>

                  <h2>2. 보관 및 파기</h2>
                  <p>수집한 정보는 서비스 이용 기간 동안 보관하며, 회원 탈퇴(앱 내 계정 삭제) 시 지체 없이 파기합니다.</p>

                  <h2>3. 제3자 제공 및 처리 위탁</h2>
                  <p>이용자의 개인정보를 제3자에게 판매하거나 광고 목적으로 제공하지 않습니다.
                     로그인(카카오, Apple) 및 푸시 알림(Apple APNs) 처리를 위해 해당 사업자의 API를 이용합니다.</p>

                  <h2>4. 이용자의 권리</h2>
                  <p>앱 내 설정에서 로그아웃 및 계정 삭제를 할 수 있으며, 계정 삭제 시 관련 데이터가 모두 삭제됩니다.</p>

                  <h2>5. 문의</h2>
                  <p>개인정보 관련 문의: <a href="mailto:fkdl4862@gmail.com">fkdl4862@gmail.com</a></p>
                </body></html>
                """;
    }
}
