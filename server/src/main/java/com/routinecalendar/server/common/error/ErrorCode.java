package com.routinecalendar.server.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 모든 도메인 에러를 한 곳에 모은 카탈로그.
 * (HTTP 상태 + 클라이언트가 분기할 수 있는 코드 + 기본 메시지)
 */
@Getter
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "입력값이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 오류가 발생했습니다."),

    // 인증
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

    // 루틴
    ROUTINE_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTINE_404", "루틴을 찾을 수 없습니다."),
    ROUTINE_FORBIDDEN(HttpStatus.FORBIDDEN, "ROUTINE_403", "접근할 수 없는 루틴입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
