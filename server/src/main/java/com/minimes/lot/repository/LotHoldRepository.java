package com.minimes.lot.repository;

import com.minimes.lot.domain.LotHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LotHoldRepository extends JpaRepository<LotHold, Long> {

    /** 이 LOT이 원인인 보류 건 (원인 LOT은 다 써서 보류 대상이 아니었을 수 있다) */
    List<LotHold> findByOriginLotNo(String originLotNo);
}
