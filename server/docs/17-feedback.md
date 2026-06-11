# 17 — feedback 도메인 (피드백 / 기능 요청)

> [← 16 routine 도메인](16-routine.md) · 다음: [00 핵심 개념으로 ↺](00-core-concepts.md)

대상 파일: `feedback/domain/Feedback.java`, `feedback/repository/FeedbackRepository.java`, `feedback/dto/FeedbackDtos.java`, `feedback/service/FeedbackService.java`, `feedback/controller/FeedbackController.java`

사용자가 앱에서 의견·기능 제안을 남기는 가장 단순한 도메인. 운영자는 [15 AdminController](15-web-public-endpoints.md)의 `/admin/feedback`으로 모아 본다.

---

## `Feedback.java`

```java
@Entity @Table(name = "feedback")
public class Feedback {
    @Id @GeneratedValue(IDENTITY) Long id;
    @ManyToOne(LAZY) @JoinColumn("user_id") User user;   // 작성자 (탈퇴 시 NULL)
    @Column(length = 2000) String content;
    @CreationTimestamp Instant createdAt;
    public Feedback(User user, String content) { ... }
}
```
- 작성자 + 내용 + 시각. User와 LAZY 다대일.
- **작성자 탈퇴 보존**: FK는 `ON DELETE SET NULL`([12 V4](12-database-schema.md)) — 유저가 삭제돼도 피드백 내용은 남고 작성자만 NULL이 된다(다른 테이블의 CASCADE와 대비되는 선택).

---

## Repository / DTO

```java
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    // 관리자 페이지에서 최신순 조회 (findAll + Sort 또는 createdAt DESC)
}

public record CreateFeedbackRequest(@NotBlank @Size(max = 2000) String content) {}
```
- 입력 검증: `@NotBlank`(공백만 금지) + `@Size(max=2000)`(길이 제한). DB `VARCHAR(2000)`와 짝.
- 조회는 `idx_feedback_created(created_at DESC)`로 최신순 가속.

---

## `FeedbackService.java` / `FeedbackController.java`

```java
    @Transactional
    public void create(Long userId, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        feedbackRepository.save(new Feedback(user, content.trim()));   // 앞뒤 공백 제거
        log.info("[피드백] 접수 userId={} length={}", userId, content.trim().length());
    }

    // Controller
    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@AuthenticationPrincipal Long userId,
                       @Valid @RequestBody CreateFeedbackRequest request) {
        feedbackService.create(userId, request.content());
    }
```
- **로그인 필요**: `@AuthenticationPrincipal`로 작성자를 토큰에서 얻는다(누가 남겼는지 추적).
- 내용은 `trim()` 후 저장. 성공 시 204.
- 운영자 조회는 [15 `/admin/feedback`](15-web-public-endpoints.md)(ADMIN_KEY 가드)에서 HTML 표로.

### 피드백 엔드포인트

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/feedback` | `{ content }` 피드백 작성(로그인 필요, 204) |
| GET | `/admin/feedback?key=…` | 운영자 조회([15](15-web-public-endpoints.md)) |

---

> 끝. 처음으로 → [00 핵심 개념](00-core-concepts.md)
