package com.minimes.lot.service;

import java.util.List;

/**
 * 실적 하나의 LOT: 투입한 LOT들과 양품으로 만든 LOT들.
 *
 * @param outputLotNos 만든 LOT. 보통 하나이고, 포장처럼 LOT 크기가 정해진 공정은 여러 개. 양품이 0개면 빈 목록
 */
public record ResultLots(List<String> outputLotNos, List<LotInput> inputs) {

    public static final ResultLots NONE = new ResultLots(List.of(), List.of());
}
