package com.routinecalendar.server.web;

import com.routinecalendar.server.common.AppTime;
import com.routinecalendar.server.config.AdminProperties;
import com.routinecalendar.server.feedback.domain.Feedback;
import com.routinecalendar.server.feedback.repository.FeedbackRepository;
import com.routinecalendar.server.user.domain.User;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 전용 간이 페이지. 인증 대신 키(?key=)로 보호한다(개인 운영용).
 */
@RestController
public class AdminController {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(AppTime.KST);

    private final FeedbackRepository feedbackRepository;
    private final AdminProperties adminProperties;

    public AdminController(FeedbackRepository feedbackRepository, AdminProperties adminProperties) {
        this.feedbackRepository = feedbackRepository;
        this.adminProperties = adminProperties;
    }

    @GetMapping(value = "/admin/feedback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> feedback(@RequestParam(required = false) String key) {
        String configured = adminProperties.key();
        // 키가 환경변수로 설정되지 않았으면(빈 값) 항상 잠금 → 빈 key로 우회 불가
        boolean denied = configured == null || configured.isBlank() || !configured.equals(key);
        if (denied) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.TEXT_HTML)
                    .body("<h2>접근 권한이 없습니다.</h2>");
        }

        List<Feedback> list = feedbackRepository.findAllWithUser();
        StringBuilder rows = new StringBuilder();
        for (Feedback f : list) {
            User u = f.getUser();
            String who = u != null ? esc(u.getNickname()) + " <span class=muted>@" + esc(u.getHandle()) + "</span>"
                                   : "<span class=muted>(탈퇴 사용자)</span>";
            rows.append("<tr><td class=muted>").append(f.getId()).append("</td>")
                .append("<td>").append(who).append("</td>")
                .append("<td>").append(esc(f.getContent()).replace("\n", "<br>")).append("</td>")
                .append("<td class=muted nowrap>").append(FMT.format(f.getCreatedAt())).append("</td></tr>");
        }
        if (list.isEmpty()) {
            rows.append("<tr><td colspan=4 class=muted style='text-align:center;padding:32px'>아직 피드백이 없어요.</td></tr>");
        }

        String html = """
                <!doctype html><html lang=ko><head><meta charset=utf-8>
                <meta name=viewport content="width=device-width, initial-scale=1">
                <title>피드백 (%d건)</title>
                <style>
                  body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;margin:0;padding:24px;color:#1c1c1e;background:#f7f7f8}
                  h1{font-size:20px;margin:0 0 16px}
                  table{border-collapse:collapse;width:100%%;background:#fff;border-radius:12px;overflow:hidden;
                        box-shadow:0 1px 3px rgba(0,0,0,.08)}
                  th,td{padding:12px 14px;text-align:left;font-size:14px;border-bottom:1px solid #eee;vertical-align:top}
                  th{background:#fafafa;font-size:12px;color:#666;font-weight:600}
                  td{line-height:1.5}
                  .muted{color:#999;font-size:12px}
                  .nowrap{white-space:nowrap}
                  tr:last-child td{border-bottom:none}
                </style></head><body>
                <h1>피드백 · 기능 제안 <span class=muted>(%d건)</span></h1>
                <table>
                  <tr><th>#</th><th>작성자</th><th>내용</th><th>작성 시각(KST)</th></tr>
                  %s
                </table>
                </body></html>
                """.formatted(list.size(), list.size(), rows);

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    /** HTML 이스케이프 (피드백 내용 내 스크립트 주입 방지). */
    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
