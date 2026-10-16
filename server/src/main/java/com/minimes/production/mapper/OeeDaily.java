package com.minimes.production.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 설비 하루의 OEE와 그 근거 숫자. 비율은 모두 %.
 *
 * @param availability 시간가동률 = 가동 시간 ÷ 계획 시간
 * @param performance  성능가동률 = 이론 사이클타임 × 생산 수량 ÷ 가동 시간
 * @param quality      양품률 = 양품 ÷ 생산 수량
 * @param oee          세 값의 곱
 */
public record OeeDaily(
        LocalDate prodDate,
        String equipCode,
        String source,
        int plannedMin,
        int downtimeMin,
        int runMin,
        int prodQty,
        int goodQty,
        BigDecimal idealCtMin,
        BigDecimal availability,
        BigDecimal performance,
        BigDecimal quality,
        BigDecimal oee
) {
}
