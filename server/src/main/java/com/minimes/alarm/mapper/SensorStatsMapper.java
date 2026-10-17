package com.minimes.alarm.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 관리한계 계산용 통계. SQL은 resources/mapper/alarm/SensorStatsMapper.xml에 있다. */
@Mapper
public interface SensorStatsMapper {

    /** 자동 수집 설비마다 [from, to) 기간 가동(RUN) 데이터의 평균과 표준편차 */
    List<SensorStats> selectRunStats(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
