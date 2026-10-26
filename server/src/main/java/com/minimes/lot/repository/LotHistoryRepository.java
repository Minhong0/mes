package com.minimes.lot.repository;

import com.minimes.lot.domain.LotEventType;
import com.minimes.lot.domain.LotHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LotHistoryRepository extends JpaRepository<LotHistory, Long> {

    List<LotHistory> findByLotNoOrderByEventAtAscHistoryIdAsc(String lotNo);

    /** 여러 LOT의 한 종류 이력, 최근 것부터. 보류 목록이 LOT마다 마지막 보류를 고를 때 쓴다 */
    List<LotHistory> findByLotNoInAndEventTypeOrderByHistoryIdDesc(Collection<String> lotNos, LotEventType eventType);

    /** 실적들이 만든 투입·산출 이력. 실적 응답에 LOT을 붙이고, 실적을 취소할 때 되돌릴 것을 찾는다. */
    List<LotHistory> findByResultIdInAndEventTypeInOrderByHistoryId(Collection<Long> resultIds,
                                                                    Collection<LotEventType> eventTypes);
}
