package com.minimes.lot.repository;

import com.minimes.lot.domain.LotHoldTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LotHoldTargetRepository extends JpaRepository<LotHoldTarget, Long> {

    /** LOT들이 아직 묶여 있는 보류 건 */
    List<LotHoldTarget> findByLotNoInAndReleasedAtIsNull(Collection<String> lotNos);
}
