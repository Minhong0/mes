package com.minimes.workorder.repository;

import com.minimes.workorder.domain.WorkOrder;
import com.minimes.workorder.domain.WorkOrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 작업지시 응답에는 공정 목록이 항상 들어가므로, @EntityGraph로 작업지시와 공정을 한 번의 쿼리로 함께 읽는다. */
public interface WorkOrderRepository extends JpaRepository<WorkOrder, String> {

    @EntityGraph(attributePaths = "operations")
    Optional<WorkOrder> findWithOperationsByWoNo(String woNo);

    @EntityGraph(attributePaths = "operations")
    List<WorkOrder> findByStatusOrderByWoNoDesc(WorkOrderStatus status);

    @EntityGraph(attributePaths = "operations")
    List<WorkOrder> findAllByOrderByWoNoDesc();

    /** 오늘 날짜로 시작하는 번호 중 가장 큰 것 (예: WO-261008-003). 다음 번호를 정할 때 쓴다. */
    Optional<WorkOrder> findTopByWoNoStartingWithOrderByWoNoDesc(String prefix);
}
