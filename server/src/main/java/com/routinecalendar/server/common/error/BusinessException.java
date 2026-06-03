package com.routinecalendar.server.common.error;

import lombok.Getter;

/**
 * 모든 도메인 예외의 공통 타입. {@link ErrorCode}만 던지면
 * {@link GlobalExceptionHandler}가 상태/코드/메시지를 일관되게 응답한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
