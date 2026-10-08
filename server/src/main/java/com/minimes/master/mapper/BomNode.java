package com.minimes.master.mapper;

import java.math.BigDecimal;

/**
 * BOM 트리의 한 줄.
 *
 * @param bomLevel      최상위 품목이 0, 그 하위가 1 ...
 * @param qtyPerParent  바로 위 품목 1개에 들어가는 수량
 * @param totalQty      최상위 품목 1개를 만들 때 필요한 총수량 (위 단계 소요량을 모두 곱한 값)
 * @param processCode   이 품목이 투입되는 공정
 */
public record BomNode(
        int bomLevel,
        String parentItemCode,
        String itemCode,
        String itemName,
        String itemType,
        String unit,
        BigDecimal qtyPerParent,
        BigDecimal totalQty,
        String processCode
) {
}
