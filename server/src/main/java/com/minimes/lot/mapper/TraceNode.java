package com.minimes.lot.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LOT 추적 트리의 한 줄.
 *
 * @param traceLevel  시작 LOT에서 몇 단계 떨어졌는가 (바로 투입·산출이 1)
 * @param parentLotNo 트리에서 바로 위 LOT (역방향이면 이 LOT이 들어간 곳, 정방향이면 이 LOT을 만든 재료)
 * @param lastProcess 이 LOT을 만든 공정 (원자재 LOT은 null)
 * @param linkQty     위 LOT과 이어진 수량 (투입 수량)
 * @param resultId    이어 준 실적
 */
public record TraceNode(
        int traceLevel,
        String parentLotNo,
        String lotNo,
        String itemCode,
        String itemName,
        String itemType,
        String lastProcess,
        String processName,
        BigDecimal linkQty,
        String status,
        String woNo,
        Long resultId,
        LocalDateTime linkedAt
) {
}
