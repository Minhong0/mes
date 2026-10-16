package com.minimes.equipment.service;

/** 설비 메시지 한 건을 새로 저장했다 (중복·모르는 설비는 제외). 트랜잭션이 커밋된 뒤에 듣는 쪽이 받는다. */
public record EquipmentDataSavedEvent(String equipCode) {
}
