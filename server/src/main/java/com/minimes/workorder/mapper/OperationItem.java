package com.minimes.workorder.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * POP 작업 목록의 한 줄: 작업장에서 할 공정 작업.
 *
 * @param prevGoodQty 앞 공정의 양품 수량 (이 공정이 받을 수 있는 수량). 첫 공정이면 null
 */
public record OperationItem(
        long woOpId,
        String woNo,
        int seq,
        String processCode,
        String processName,
        String wcCode,
        String status,
        int goodQty,
        int defectQty,
        LocalDateTime startedAt,
        String itemCode,
        String itemName,
        int planQty,
        LocalDate dueDate,
        String woStatus,
        Integer prevGoodQty
) {
}
