package com.routinecalendar.server.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * iOS Universal Links 지원.
 * - /.well-known/apple-app-site-association : Apple이 도메인 소유/연결을 검증하는 AASA 파일.
 * - /add-friend : 앱 미설치(브라우저 등) 시 보이는 폴백 페이지.
 */
@RestController
public class WellKnownController {

    // TeamID(DEVELOPMENT_TEAM) + BundleID. 둘 중 하나라도 바뀌면 여기도 수정.
    private static final String APP_ID = "DBDJ2HDBU2.com.i3inni.routinecalendar";

    /**
     * AASA. 반드시 application/json 으로, 리다이렉트 없이, HTTPS로 응답되어야 한다.
     * components: /add-friend 경로 + id 쿼리를 가진 링크만 앱이 가로채도록 매칭.
     */
    @GetMapping(value = "/.well-known/apple-app-site-association",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> appleAppSiteAssociation() {
        return Map.of(
                "applinks", Map.of(
                        "details", List.of(Map.of(
                                "appIDs", List.of(APP_ID),
                                "components", List.of(Map.of(
                                        "/", "/add-friend",
                                        "?", Map.of("id", "?*")
                                ))
                        ))
                )
        );
    }

    /** 앱이 설치돼 있으면 이 페이지 대신 앱이 열린다. 아니면 안내 + 스킴 폴백 버튼. */
    @GetMapping(value = "/add-friend", produces = MediaType.TEXT_HTML_VALUE)
    public String addFriendFallback(@RequestParam(required = false) String id) {
        String safeId = id == null ? "" : id.replaceAll("[^A-Za-z0-9]", "");
        return """
                <!doctype html>
                <html lang="ko"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>같이해 친구추가</title>
                <style>
                  body{font-family:-apple-system,sans-serif;text-align:center;padding:48px 24px;color:#222}
                  .id{font-size:28px;font-weight:700;letter-spacing:3px;margin:16px 0}
                  a.btn{display:inline-block;margin-top:24px;padding:14px 28px;background:#111;color:#fff;
                        border-radius:12px;text-decoration:none;font-weight:600}
                </style></head>
                <body>
                  <h2>같이해에서 함께 루틴 해요</h2>
                  <p>아래 ID로 친구추가할 수 있어요</p>
                  <div class="id">%s</div>
                  <a class="btn" href="routinecalendar://add-friend?id=%s">앱에서 열기</a>
                </body></html>
                """.formatted(safeId, safeId);
    }
}
