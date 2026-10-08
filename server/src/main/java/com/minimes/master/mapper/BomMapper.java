package com.minimes.master.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * BOM 전개 쿼리. SQL은 resources/mapper/master/BomMapper.xml에 있다.
 * 재귀 CTE처럼 SQL을 직접 써야 읽기 쉬운 쿼리는 JPA 대신 MyBatis로 작성한다.
 */
@Mapper
public interface BomMapper {

    /** 품목 하나를 최상위로 BOM을 끝까지 펼친다. 결과는 트리를 위에서 아래로 그린 순서(깊이 우선). */
    List<BomNode> selectBomTree(String itemCode);
}
