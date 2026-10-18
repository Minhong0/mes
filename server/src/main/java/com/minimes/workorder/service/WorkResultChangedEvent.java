package com.minimes.workorder.service;

import java.time.LocalDate;

/**
 * 설비에 걸린 실적이 생기거나 취소됐다. 그날 일별 집계의 불량 수량(OEE 품질)이 달라지므로 다시 집계하라고 알린다.
 * prodDate: 실적을 등록한 날 (집계가 불량을 그날로 센다)
 */
public record WorkResultChangedEvent(String equipCode, LocalDate prodDate) {
}
