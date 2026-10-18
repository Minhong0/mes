package com.minimes.master.repository;

import com.minimes.master.domain.Bom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BomRepository extends JpaRepository<Bom, Integer> {

    /** 이 공정에서 상위 품목에 들어가는 하위 품목들 */
    List<Bom> findByParentItemCodeAndProcessCode(String parentItemCode, String processCode);
}
