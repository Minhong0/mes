package com.minimes.workorder.service;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.master.domain.Routing;
import com.minimes.master.repository.ItemRepository;
import com.minimes.master.repository.RoutingRepository;
import com.minimes.workorder.domain.WorkOrder;
import com.minimes.workorder.domain.WorkOrderStatus;
import com.minimes.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 작업지시 생성과 상태 변경. 상태 규칙은 WorkOrder 엔티티가 맡는다. */
@Service
@RequiredArgsConstructor
@Transactional
public class WorkOrderService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final WorkOrderRepository workOrderRepository;
    private final ItemRepository itemRepository;
    private final RoutingRepository routingRepository;
    private final Clock clock;

    public WorkOrder create(String itemCode, int planQty, LocalDate dueDate, String note) {
        if (!itemRepository.existsById(itemCode)) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND, "품목을 찾을 수 없습니다: " + itemCode);
        }
        List<Routing> routings = routingRepository.findByItemCodeOrderBySeq(itemCode);
        LocalDateTime now = LocalDateTime.now(clock);
        WorkOrder wo = WorkOrder.create(nextWoNo(now.toLocalDate()), itemCode, planQty, dueDate, note, routings, now);
        return workOrderRepository.save(wo);
    }

    /**
     * 작업지시 번호: WO-yyMMdd-001 (그날의 일련번호).
     * 두 사람이 같은 순간에 만들면 같은 번호가 나올 수 있고, 이때는 기본키 중복으로 저장이 실패한다.
     */
    String nextWoNo(LocalDate today) {
        String prefix = "WO-" + today.format(NO_DATE) + "-";
        int next = workOrderRepository.findTopByWoNoStartingWithOrderByWoNoDesc(prefix)
                .map(last -> Integer.parseInt(last.getWoNo().substring(prefix.length())) + 1)
                .orElse(1);
        return prefix + "%03d".formatted(next);
    }

    @Transactional(readOnly = true)
    public List<WorkOrder> findAll(WorkOrderStatus status) {
        return status == null
                ? workOrderRepository.findAllByOrderByWoNoDesc()
                : workOrderRepository.findByStatusOrderByWoNoDesc(status);
    }

    @Transactional(readOnly = true)
    public WorkOrder get(String woNo) {
        return workOrderRepository.findWithOperationsByWoNo(woNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_ORDER_NOT_FOUND, "작업지시를 찾을 수 없습니다: " + woNo));
    }

    // 아래 메서드들은 save()를 부르지 않는다. 바꾼 값은 커밋할 때 UPDATE 된다 (변경 감지).

    public WorkOrder start(String woNo) {
        WorkOrder wo = get(woNo);
        wo.start(LocalDateTime.now(clock));
        return wo;
    }

    public WorkOrder complete(String woNo) {
        WorkOrder wo = get(woNo);
        wo.complete(LocalDateTime.now(clock));
        return wo;
    }

    public WorkOrder close(String woNo) {
        WorkOrder wo = get(woNo);
        wo.close();
        return wo;
    }
}
