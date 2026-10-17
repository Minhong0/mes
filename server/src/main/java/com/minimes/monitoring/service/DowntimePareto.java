package com.minimes.monitoring.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 정지 사유 파레토 (JSON 응답). 정지 시간이 많은 사유부터, 누적 비율과 함께.
 *
 * @param from      기간 시작 (포함)
 * @param to        기간 끝 (포함)
 * @param equipCode null이면 전체 설비
 * @param byReason  true면 소분류(사유)별, false면 대분류별
 */
public record DowntimePareto(LocalDate from, LocalDate to, String equipCode, boolean byReason,
                             int totalMin, int stopCount, List<Item> items) {

    /** 누적 비율이 이 값에 이를 때까지의 사유를 "중점 관리 대상"으로 표시한다 (80/20 법칙) */
    public static final BigDecimal VITAL_FEW_PERCENT = BigDecimal.valueOf(80);

    /**
     * @param sharePct      전체 정지 시간 중 이 사유의 비율 (%)
     * @param cumulativePct 첫 줄부터 이 줄까지의 누적 비율 (%)
     * @param vitalFew      누적 80%에 들어가는 사유인가 (80%를 넘기는 첫 줄까지 포함)
     */
    public record Item(String dtCat, String dtCatName, String dtSub, String dtSubName, int stopCount, int totalMin,
                       BigDecimal sharePct, BigDecimal cumulativePct, boolean vitalFew) {
    }
}
