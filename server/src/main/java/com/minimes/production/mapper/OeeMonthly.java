package com.minimes.production.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 설비 한 달의 OEE (%). 일별 OEE의 평균이 아니라 시간과 수량을 먼저 합산해 계산한 값이다. */
public record OeeMonthly(
        LocalDate prodMonth,
        String equipCode,
        String source,
        BigDecimal availability,
        BigDecimal performance,
        BigDecimal quality,
        BigDecimal oee
) {
}
