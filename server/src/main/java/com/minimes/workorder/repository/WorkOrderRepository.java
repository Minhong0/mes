package com.minimes.workorder.repository;

import com.minimes.workorder.domain.WorkOrder;
import com.minimes.workorder.domain.WorkOrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 작업지시 응답에는 공정 목록(operations)이 항상 들어간다.
 * operations는 지연 로딩(LAZY)이라, 그냥 조회하면 트랜잭션이 끝난 뒤 컨트롤러에서 꺼낼 때 오류가 나고,
 * 목록을 조회하면 작업지시마다 공정 조회 쿼리가 한 번씩 더 나간다 (N+1 문제).
 * @EntityGraph로 작업지시와 공정을 한 번의 쿼리로 함께 읽는다.
 */
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
