package com.minimes.lot.repository;

import com.minimes.lot.domain.Lot;
import com.minimes.lot.domain.LotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LotRepository extends JpaRepository<Lot, String> {

    /** 그날 이 품목의 마지막 LOT 번호. 다음 일련번호를 정할 때 쓴다. */
    Optional<Lot> findTopByLotNoStartingWithOrderByLotNoDesc(String prefix);

    /**
     * 투입할 LOT을 잠그고 읽는다 (SELECT ... WITH (UPDLOCK, ROWLOCK)).
     * 두 POP가 같은 LOT을 동시에 투입하면, 뒤에 온 요청은 앞 요청이 커밋할 때까지 기다렸다가 줄어든 수량을 읽는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lot l where l.lotNo in :lotNos")
    List<Lot> findForUpdate(@Param("lotNos") Collection<String> lotNos);

    /** 원자재 투입 후보: 이 품목의 쓸 수 있는 입고 LOT, 먼저 들어온 것부터 (선입선출) */
    List<Lot> findByItemCodeAndStatusAndWoNoIsNullOrderByCreatedAtAscLotNoAsc(String itemCode, LotStatus status);

    /** 반제품 투입 후보: 같은 작업지시의 앞 공정에서 만든 쓸 수 있는 LOT, 먼저 만든 것부터 */
    List<Lot> findByItemCodeAndStatusAndWoNoAndLastProcessOrderByCreatedAtAscLotNoAsc(
            String itemCode, LotStatus status, String woNo, String lastProcess);

    List<Lot> findByStatus(LotStatus status);

    /** 품목의 LOT 목록 (최근 것부터) */
    List<Lot> findTop100ByItemCodeOrderByCreatedAtDescLotNoDesc(String itemCode);

    List<Lot> findTop100ByItemCodeAndStatusOrderByCreatedAtDescLotNoDesc(String itemCode, LotStatus status);
}
