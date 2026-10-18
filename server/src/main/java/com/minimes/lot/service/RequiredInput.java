package com.minimes.lot.service;

import com.minimes.lot.domain.Lot;

import java.math.BigDecimal;

/**
 * 공정이 투입받아야 하는 LOT 관리 품목 하나.
 *
 * @param qtyPer      산출 1개(양품 + 불량)당 투입 수량
 * @param fromProcess 앞 공정 코드. 같은 작업지시의 이 공정에서 만든 LOT만 쓸 수 있다. null이면 입고한 원자재 LOT
 */
public record RequiredInput(String itemCode, BigDecimal qtyPer, String fromProcess) {

    /** 이 LOT을 여기에 투입할 수 있는가 (품목, 출처) */
    boolean accepts(Lot lot, String woNo) {
        if (!itemCode.equals(lot.getItemCode())) {
            return false;
        }
        return fromProcess == null
                ? lot.getWoNo() == null
                : woNo.equals(lot.getWoNo()) && fromProcess.equals(lot.getLastProcess());
    }
}
