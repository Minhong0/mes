package com.minimes.monitoring.mapper;

/**
 * 정지 사유 한 줄. 대분류별로 묶으면 dtSub, dtSubName은 null이다.
 *
 * @param cumulativeMin 이 줄까지의 정지 시간 누계 (많은 순서로 더함)
 * @param grandTotalMin 전체 정지 시간 합계 (모든 줄이 같은 값)
 */
public record DowntimeReasonRow(String dtCat, String dtCatName, String dtSub, String dtSubName,
                                int stopCount, int totalMin, int cumulativeMin, int grandTotalMin) {
}
