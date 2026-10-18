package com.minimes.lot.repository;

import com.minimes.lot.domain.LotGenealogy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 계보는 쓰기만 하고, 따라가는 조회(재귀 CTE)는 LotTraceMapper가 한다. */
public interface LotGenealogyRepository extends JpaRepository<LotGenealogy, Long> {

    List<LotGenealogy> findByResultId(long resultId);
}
