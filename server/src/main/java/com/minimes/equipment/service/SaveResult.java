package com.minimes.equipment.service;

/** 수신 메시지 하나를 처리한 결과 */
public enum SaveResult {
    SAVED,               // 새로 저장
    DUPLICATE,           // 이미 받은 messageId (재전송). 저장하지 않는다
    UNKNOWN_EQUIPMENT    // 기준정보에 없는 설비. 저장하지 않는다
}
