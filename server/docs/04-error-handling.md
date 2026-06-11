# 04 — 글로벌 예외 처리 (common/)

> [← 03 보안 & JWT](03-security-jwt.md) · 다음: [05 user 도메인 →](05-user.md)

대상 파일: `common/error/ErrorCode.java`, `BusinessException.java`, `ErrorResponse.java`, `GlobalExceptionHandler.java`, `common/AppTime.java`

이 프로젝트의 설계 포인트. 각 서비스에서 개별 예외 클래스를 만들지 않고, **`ErrorCode` enum + `BusinessException` + `@RestControllerAdvice`** 3종으로 통일한다.

---

## `ErrorCode.java` — 에러 카탈로그

```java
@Getter
public enum ErrorCode {
    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "입력값이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 오류가 발생했습니다."),
    // 인증 (카카오 / refresh / 애플 / dev-login)
    INVALID_KAKAO_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_1", "유효하지 않은 카카오 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_2", "유효하지 않은 refresh 토큰입니다. 다시 로그인해 주세요."),
    INVALID_APPLE_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_3", "유효하지 않은 애플 토큰입니다."),
    DEV_LOGIN_DISABLED(HttpStatus.FORBIDDEN, "AUTH_403_1", "개발용 로그인이 비활성화되어 있습니다."),
    // 사용자
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404", "사용자를 찾을 수 없습니다."),
    // 친구
    CANNOT_FRIEND_SELF(HttpStatus.BAD_REQUEST, "FRIEND_400_1", "자기 자신은 추가할 수 없습니다."),
    ALREADY_FRIEND(HttpStatus.CONFLICT, "FRIEND_409_1", "이미 친구입니다."),
    FRIEND_REQUEST_ALREADY_SENT(HttpStatus.CONFLICT, "FRIEND_409_2", "이미 친구 요청을 보냈습니다."),
    FRIEND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "FRIEND_404_1", "친구 요청을 찾을 수 없습니다."),
    FRIEND_REQUEST_FORBIDDEN(HttpStatus.FORBIDDEN, "FRIEND_403_1", "처리할 수 없는 친구 요청입니다."),
    NOT_FRIEND(HttpStatus.FORBIDDEN, "FRIEND_403_2", "친구가 아닌 사용자입니다."),
    NUDGE_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "FRIEND_429", "잠시 후에 다시 자극할 수 있어요."),
    // 카카오 연동 (친구 찾기)
    KAKAO_ALREADY_LINKED(HttpStatus.CONFLICT, "KAKAO_409", "이미 다른 계정에 연동된 카카오입니다."),
    ACCOUNT_HAS_OTHER_KAKAO(HttpStatus.CONFLICT, "KAKAO_409_2", "이 계정에는 이미 다른 카카오가 연동돼 있어요."),
    KAKAO_FRIENDS_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "KAKAO_403_3", "카카오 친구 목록 제공 동의가 필요해요."),
    // 루틴
    ROUTINE_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTINE_404", "루틴을 찾을 수 없습니다."),
    ROUTINE_FORBIDDEN(HttpStatus.FORBIDDEN, "ROUTINE_403", "접근할 수 없는 루틴입니다.");

    private final HttpStatus status;  // HTTP 상태코드
    private final String code;        // 클라이언트가 분기에 쓰는 문자열 코드
    private final String message;     // 사용자 메시지 기본값
    ErrorCode(HttpStatus status, String code, String message) { ... }
}
```
- **enum 상수마다 (HTTP 상태 + 코드 + 메시지)를 묶음**. 모든 에러를 한 파일에서 한눈에.
- `code`("FRIEND_409_1")는 iOS가 보고 분기(예: "이미 친구"면 다른 UI). HTTP 상태만으론 구분 부족하니 별도 코드.
- `@Getter`(롬복)로 `getStatus()/getCode()/getMessage()` 생성.

---

## `BusinessException.java`

```java
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    public BusinessException(ErrorCode errorCode) { super(errorCode.getMessage()); this.errorCode = errorCode; }
    public BusinessException(ErrorCode errorCode, String message) { super(message); this.errorCode = errorCode; }
}
```
- 비즈니스 규칙 위반을 표현하는 **단일 예외 타입**. 서비스는 그냥 `throw new BusinessException(ErrorCode.ALREADY_FRIEND)` 한 줄.
- **`RuntimeException` 상속 → 언체크 예외**라 메서드 시그니처에 `throws` 안 달아도 됨. 또한 **트랜잭션은 기본적으로 런타임 예외에서 롤백**된다(체크예외는 기본 롤백 안 함 — 면접 포인트).

---

## `ErrorResponse.java`

```java
public record ErrorResponse(String code, String message) {
    public static ErrorResponse of(ErrorCode e) { return new ErrorResponse(e.getCode(), e.getMessage()); }
    public static ErrorResponse of(ErrorCode e, String message) { return new ErrorResponse(e.getCode(), message); }
}
```
- 클라이언트에게 나가는 에러 본문 형태: `{"code": "...", "message": "..."}`. 정적 팩토리 `of`로 생성 편의 제공.

---

## `GlobalExceptionHandler.java`

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
```
- **`@RestControllerAdvice`**: 모든 컨트롤러에서 튀어나온 예외를 가로채는 전역 핸들러. `@ExceptionHandler` 메서드로 예외 타입별 응답 정의. `@ResponseBody` 포함이라 반환값이 JSON 직렬화.
- `@Slf4j`(롬복): `log` 로거 필드 자동 생성.

```java
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, e.getMessage()));
    }
```
- 우리가 의도적으로 던진 비즈니스 예외 → ErrorCode의 상태/코드/메시지로 변환해 응답.

```java
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, detail));
    }
```
- `@Valid` 검증 실패 시 스프링이 던지는 예외. 어떤 필드가 왜 틀렸는지(`field: message`)를 모아 400으로 응답.
- 스트림: `getFieldErrors()` 리스트를 돌며 문자열로 매핑 후 콤마로 join.

```java
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }
```
- 위에서 안 잡힌 **모든 예외의 최종 안전망**. 스택트레이스는 **로그에만** 남기고, 클라이언트엔 내부 정보 없이 일반 500 메시지만(정보 노출 방지).
- 알려진 한계: 잘못된 Content-Type 같은 스프링 MVC 예외가 여기로 빠져 500이 됨 → 추후 `ResponseEntityExceptionHandler` 상속으로 415/400 매핑 가능.

> **인증 실패(401)는 여기서 안 잡는다.** 필터 단계에서 막히므로 [02 SecurityConfig](02-config-layer.md)의 EntryPoint가 담당. 예외 처리 책임이 "필터 단(401) vs 컨트롤러 단(그 외)"으로 나뉜다.

---

## `common/AppTime.java` — "오늘"을 KST로 통일

```java
public final class AppTime {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private AppTime() {}
    public static LocalDate today() { return LocalDate.now(KST); }
}
```
- 서버가 어느 타임존에서 돌든 **"오늘"은 항상 한국 기준**으로 계산. 요약 날짜 경계가 사용자 체감과 일치하게.
- `private` 생성자 + `static`만 → 인스턴스화 못 하는 유틸 클래스.
- [09 summary](09-summary.md), [07 friend](07-friend.md)에서 사용.

---

> 다음: [05 user 도메인 →](05-user.md)
