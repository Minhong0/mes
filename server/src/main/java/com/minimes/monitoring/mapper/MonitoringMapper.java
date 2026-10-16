package com.minimes.monitoring.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 모니터링 화면용 조회. SQL은 resources/mapper/monitoring/MonitoringMapper.xml에 있다.
 * 화면 하나를 그리는 데 테이블 다섯 개가 필요해서, JPA 엔티티를 여러 번 조회하는 대신 SQL 한 번으로 모은다.
 */
@Mapper
public interface MonitoringMapper {

    /** 자동 수집 설비(CNC01~05)마다 현재 상태, 진행 중인 정지, 마지막 센서값 */
    List<EquipmentNow> selectEquipmentNow();
}
