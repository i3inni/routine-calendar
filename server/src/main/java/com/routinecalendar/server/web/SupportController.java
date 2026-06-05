package com.routinecalendar.server.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 고객 지원 페이지. App Store 제출 시 필요한 지원(Support) URL(/support).
 */
@RestController
public class SupportController {

    @GetMapping(value = "/support", produces = MediaType.TEXT_HTML_VALUE)
    public String support() {
        return """
                <!doctype html>
                <html lang="ko"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>같이해 고객 지원</title>
                <style>
                  body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;max-width:640px;margin:0 auto;
                       padding:48px 20px;color:#222;line-height:1.7;text-align:center}
                  h1{font-size:26px} h2{font-size:18px;margin-top:32px;text-align:left}
                  p,li{font-size:15px} ul{text-align:left}
                  a.btn{display:inline-block;margin-top:20px;padding:14px 28px;background:#111;color:#fff;
                        border-radius:12px;text-decoration:none;font-weight:600}
                  .muted{color:#888;font-size:13px;margin-top:40px}
                </style></head>
                <body>
                  <h1>같이해 고객 지원</h1>
                  <p>친구와 함께 루틴을 만들어가는 앱, ‘같이해’를 이용해 주셔서 감사합니다.</p>

                  <h2>자주 묻는 질문</h2>
                  <ul>
                    <li><b>친구 추가는 어떻게 하나요?</b> 친구 탭의 ‘+’에서 상대의 ID를 입력하거나, 내 ID를 공유하세요.</li>
                    <li><b>알림이 오지 않아요.</b> 설정 → 알림 권한이 켜져 있는지 확인해 주세요.</li>
                    <li><b>계정을 삭제하고 싶어요.</b> 설정 맨 아래 ‘계정 삭제’에서 가능합니다(3일 이내 재로그인 시 취소).</li>
                  </ul>

                  <h2>문의</h2>
                  <p>해결되지 않는 문제는 아래로 연락 주세요.</p>
                  <a class="btn" href="mailto:fkdl4862@gmail.com">이메일 문의하기</a>

                  <p class="muted">fkdl4862@gmail.com</p>
                </body></html>
                """;
    }
}
