package com.minimes.lot.service;

import java.util.List;

/**
 * 실적 하나의 LOT: 투입한 LOT들과 양품으로 만든 LOT.
 *
 * @param outputLotNo 양품이 0개면 null
 */
public record ResultLots(String outputLotNo, List<LotInput> inputs) {

    public static final ResultLots NONE = new ResultLots(null, List.of());
}
