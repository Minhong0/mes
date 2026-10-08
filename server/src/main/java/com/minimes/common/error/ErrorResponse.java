package com.minimes.common.error;

import java.util.List;

/**
 * 모든 오류 응답의 공통 형식.
 * 예: {"code":"INVALID_STATUS_TRANSITION","message":"...","fieldErrors":[]}
 */
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {

    public record FieldError(String field, String reason) {
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
        return new ErrorResponse(errorCode.name(), errorCode.getDefaultMessage(), fieldErrors);
    }
}
