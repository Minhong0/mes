package com.minimes.workorder.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** POP 작업 목록 조회. SQL은 resources/mapper/workorder/WorkListMapper.xml에 있다. */
@Mapper
public interface WorkListMapper {

    /** 작업장에서 아직 끝나지 않은 공정 작업. 진행 중인 것이 먼저, 그다음 납기 순 */
    List<OperationItem> selectOpenOperations(String wcCode);
}
