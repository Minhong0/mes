package com.minimes.monitoring.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 모니터링 화면용 조회. SQL은 resources/mapper/monitoring/MonitoringMapper.xml에 있다.
 * 화면 하나를 그리는 데 테이블 다섯 개가 필요해서, JPA 엔티티를 여러 번 조회하는 대신 SQL 한 번으로 모은다.
 */
@Mapper
public interface MonitoringMapper {

    /** 자동 수집 설비(CNC01~05)마다 현재 상태, 진행 중인 정지, 마지막 센서값 */
    List<EquipmentNow> selectEquipmentNow();

    /** 확인 전 알람 전체 (최근 것부터) */
    List<OpenAlarm> selectOpenAlarms();

    /**
     * 정지 사유별 정지 시간 합계, 많은 순서. [from, to) 기간에 시작해 끝난 정지만 센다.
     *
     * @param equipCode null이면 전체 설비
     * @param byReason  true면 소분류(사유)별, false면 대분류별
     */
    List<DowntimeReasonRow> selectDowntimeByReason(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                                   @Param("equipCode") String equipCode, @Param("byReason") boolean byReason);

    /** 가장 최근 정지의 시작 날짜. 정지 기록이 없으면 null */
    LocalDate selectLatestDowntimeDate();
}
