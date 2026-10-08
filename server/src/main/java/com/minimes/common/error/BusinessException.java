package com.minimes.common.error;

import lombok.Getter;

/**
 * 업무 규칙 위반을 나타내는 예외. GlobalExceptionHandler가 ErrorCode에 맞는 HTTP 응답으로 바꾼다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
