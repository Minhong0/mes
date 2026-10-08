package com.minimes.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 서버가 돌려주는 오류의 종류. 클라이언트(POP, 관리자 웹)는 HTTP 상태 대신 code로 오류를 구분한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "품목을 찾을 수 없습니다."),
    WORK_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "작업지시를 찾을 수 없습니다."),
    ROUTING_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT, "품목에 등록된 라우팅이 없습니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "현재 상태에서는 할 수 없는 작업입니다.");

    private final HttpStatus status;
    private final String defaultMessage;
}
