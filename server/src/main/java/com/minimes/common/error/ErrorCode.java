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
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "현재 상태에서는 할 수 없는 작업입니다."),
    ALARM_NOT_FOUND(HttpStatus.NOT_FOUND, "알람을 찾을 수 없습니다."),
    ALARM_ALREADY_ACKNOWLEDGED(HttpStatus.CONFLICT, "이미 확인한 알람입니다."),
    WORKER_NOT_FOUND(HttpStatus.NOT_FOUND, "작업자를 찾을 수 없습니다."),
    OPERATION_NOT_FOUND(HttpStatus.NOT_FOUND, "공정 작업을 찾을 수 없습니다."),
    RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "실적을 찾을 수 없습니다."),
    DOWNTIME_NOT_FOUND(HttpStatus.NOT_FOUND, "정지 기록을 찾을 수 없습니다."),
    LOT_NOT_FOUND(HttpStatus.NOT_FOUND, "LOT을 찾을 수 없습니다."),
    LOT_IN_USE(HttpStatus.CONFLICT, "이미 다음 공정에 투입된 LOT입니다."),
    LOT_ON_HOLD(HttpStatus.CONFLICT, "품질 보류 중인 LOT입니다."),
    INSPECTION_DONE(HttpStatus.CONFLICT, "이미 검사한 LOT입니다."),
    NOT_ALLOWED(HttpStatus.FORBIDDEN, "이 작업을 할 권한이 없습니다.");

    private final HttpStatus status;
    private final String defaultMessage;
}
