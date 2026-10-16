package com.minimes.production.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * OEE 조회. SQL은 resources/mapper/production/OeeMapper.xml에 있다.
 * 계산식은 DB 뷰(v_oee_daily, v_oee_monthly)에 한 번만 두고, 여기서는 읽기만 한다.
 * 그래서 부트캠프 실적(BOOTCAMP)과 수집 집계(MES)가 같은 식으로 계산된다.
 */
@Mapper
public interface OeeMapper {

    List<OeeDaily> selectDaily(LocalDate prodDate);

    /** month: 그 달 1일 (예: 2025-10-01) */
    List<OeeMonthly> selectMonthly(LocalDate month);
}
