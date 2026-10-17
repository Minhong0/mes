package com.minimes.alarm.mapper;

import java.math.BigDecimal;

/** 설비 한 대의 기준 기간 가동 데이터 통계. 표본이 1개뿐이면 표준편차는 null이다. */
public record SensorStats(
        String equipCode,
        int sampleCount,
        BigDecimal vibrationMean,
        BigDecimal vibrationSigma,
        BigDecimal tempMean,
        BigDecimal tempSigma
) {
}
