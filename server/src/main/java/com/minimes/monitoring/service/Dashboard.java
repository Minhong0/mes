package com.minimes.monitoring.service;

import com.minimes.monitoring.mapper.OpenAlarm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 설비 현황 대시보드 한 화면에 필요한 데이터 (JSON 응답).
 * 화면이 계산하지 않고 그대로 보여 줄 수 있게, 경과 시간·온라인 여부 같은 판단은 서버가 미리 해 둔다.
 *
 * @param factoryNow 공장 시계의 "지금" = 모든 설비 중 가장 늦은 측정 시각
 * @param prodDate   OEE를 보여 주는 날짜 (공장 시계의 오늘)
 */
public record Dashboard(LocalDateTime factoryNow, LocalDate prodDate, List<EquipmentCard> equipment) {

    /**
     * 설비 카드 한 장.
     *
     * @param online        최근 30분(공장 시계) 안에 소식이 있었나. false면 화면에 "통신 없음"
     * @param statusMinutes 지금 상태가 몇 분째인가
     * @param stop          진행 중인 정지 (가동 중이면 null)
     * @param today         오늘 생산 실적과 OEE (아직 집계 전이면 null)
     * @param alarms        확인 전 알람 (없으면 빈 목록)
     */
    public record EquipmentCard(
            String equipCode,
            String equipName,
            boolean online,
            String status,
            String statusName,
            LocalDateTime statusSince,
            Long statusMinutes,
            Stop stop,
            Integer cycleCount,
            BigDecimal spindleVibration,
            BigDecimal spindleTemp,
            LocalDateTime lastSeenAt,
            TodayOee today,
            List<OpenAlarm> alarms
    ) {
    }

    /** 진행 중인 정지: 대분류·소분류 코드와 화면에 보여 줄 이름 */
    public record Stop(String dtCat, String dtCatName, String dtSub, String dtSubName, LocalDateTime startedAt) {
    }

    /** 오늘 실적과 OEE (%). 일별 집계(production_daily)를 그대로 옮긴 값이다. */
    public record TodayOee(int prodQty, int downtimeMin, BigDecimal availability, BigDecimal performance,
                           BigDecimal quality, BigDecimal oee) {
    }
}
