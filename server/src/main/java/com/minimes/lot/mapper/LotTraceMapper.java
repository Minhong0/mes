package com.minimes.lot.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** LOT 계보를 끝까지 따라가는 재귀 CTE. SQL은 resources/mapper/lot/LotTraceMapper.xml에 있다. */
@Mapper
public interface LotTraceMapper {

    /** 역방향 추적: 이 LOT을 만드는 데 들어간 LOT들 (완제품 → 원자재) */
    List<TraceNode> selectBackward(String lotNo);

    /** 정방향 추적: 이 LOT이 들어간 LOT들 (원자재 → 완제품) */
    List<TraceNode> selectForward(String lotNo);
}
